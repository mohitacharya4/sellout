# Sellout — Flash-Sale Ticketing Platform (fair admission, no oversell, resilient checkout)

> A production-grade, event-driven ticketing backend built for the worst minute of a ticketing company's year: **100,000 buyers press *Buy* for 5,000 seats in the same second.** A virtual waiting room admits them **fairly** at a rate the backend can sustain and hands each one a signed admission token. An inventory service places **seat holds that can never overlap**, backed by an atomic Redis pre-check and a Postgres conditional update as the source of truth. An order service runs checkout as a **saga** — hold → pay → confirm → issue — with idempotency keys, a transactional outbox, timeouts, and compensation, so a flaky payment provider can never double-charge or lose a seat. Every service is observable, containerised, deployable to Kubernetes, and load-tested — and the Grafana dashboard proves the headline numbers.

> **Tagline:** *Sell out in seconds. Oversell never.*

**Why this project:** It solves a real, universally understood problem (a fixed inventory meets a demand spike) whose difficulty is *correctness under contention*, not raw throughput — which is exactly the depth a senior distributed-systems role screens for: consistency trade-offs, idempotency, sagas and compensation, backpressure and load shedding, hot-key handling, exactly-once *effects* over at-least-once delivery, and observability that proves it. It maps to a real paid category (primary ticketing, drops, enrolment, appointment slots). Its edge over generic "microservices demo" projects: **measurable invariants** (`oversell_total == 0`, every saga terminates, every expired hold releases) verified by a committed load test against a real cluster.

**Positioning and scope guard:** Sellout is the Java/JVM large-scale-systems flagship, complementary to the AI-agent portfolio (Atlas, personal-twin). It is *local-first*: everything runs on Docker Compose and a local kind cluster; AWS via Terraform is a one-shot demo deploy, never a standing bill. Build the thin vertical slice in §13 first (inventory holds with a concurrency proof, then the saga, then admission), ship end-to-end with the dashboard, *then* expand.

---

## 1. The concrete problem (see PROBLEM.md for the deep-dive)

A ticket on-sale is a demand wall. A read-then-write seat table oversells under concurrency, the fastest bot wins, and an unreliable payment provider turns retries into double charges and abandoned holds into lost seats. Sellout admits buyers fairly, holds seats with a database-enforced guarantee, runs checkout as a saga that always terminates, and proves it with metrics under a 100k-user spike.

**Who pays:** primary ticketing platforms and anyone selling fixed inventory into a spike. Per-ticket fee plus platform fee.

---

## 2. Why it is genuinely hard (not a CRUD app with more pods)

| Concern | What Sellout does | The senior signal |
|---|---|---|
| **Contention on a hot row** | Thousands of requests target the same few seats. A Redis Lua script pre-checks and reserves atomically (sub-millisecond, in-memory), then Postgres `UPDATE … WHERE status = 'AVAILABLE'` is the source of truth. A unique constraint on sold seats is the last-line guard. | Knows the difference between a cache and a source of truth; designs for the DB to be correct even when the cache is wrong. |
| **Fairness and backpressure** | A per-event waiting room (Redis sorted set keyed by arrival) releases N buyers/s based on downstream capacity. The gateway rate-limits per user and rejects anything without a valid admission token with `429`/`403`. | Load shedding at the edge; the backend sees a controlled rate, never the raw spike. |
| **Unreliable dependency** | The PSP simulator injects latency, 5xx, timeouts, and duplicate/late webhooks. The saga has explicit states, deadlines, and compensations; every side effect carries an idempotency key. | Exactly-once *effects* over at-least-once delivery; compensation, not rollback. |
| **Atomic publish** | A state change and its event are written in one transaction to an outbox table; a relay publishes to Kafka. No dual-write. | Understands the dual-write problem and the standard fix. |
| **Time and expiry** | Holds carry a TTL. A sweeper releases expired holds; Redis key expiry is a hint, Postgres `expires_at` is the truth. Fencing tokens stop a late confirm from resurrecting an expired hold. | Handles the "delayed message after expiry" race that most implementations miss. |
| **Elastic scale** | Stateless services; KEDA scales consumers on Kafka lag and admission on queue depth. Virtual threads keep per-request cost low. | Autoscaling driven by business signals, not CPU. |
| **Proof** | `oversell_total`, `saga_stuck`, `holds_expired_total`, hold p99 on a committed Grafana dashboard; k6 spike plus a reconciliation job in CI. | Claims are measured, not asserted. |

---

## 3. Domain model and the end-to-end flow

### 3.1 Core entities

```
Event          { id, organizerId, name, startsAt, saleOpensAt, saleClosesAt, status }
Section        { id, eventId, name, priceMinor, currency }
Seat           { id, eventId, sectionId, row, number, status: AVAILABLE|HELD|SOLD, version }
Hold           { id, eventId, seatIds[], userId, fencingToken, expiresAt, status: ACTIVE|CONFIRMED|RELEASED|EXPIRED }
Order          { id, userId, eventId, holdId, amountMinor, idempotencyKey, state, paymentRef, deadlineAt, updatedAt }
Ticket         { id, orderId, eventId, seatId, userId, issuedAt }     -- UNIQUE (eventId, seatId)
AdmissionToken { sub, eventId, iat, exp, jti, sig }                    -- signed, not stored
```

Seats are the contended resource. A **hold** is a short lease (default 5 min) over one to six seats for one user. An **order** is the saga instance. A **ticket** exists only for a `CONFIRMED` order, and its uniqueness constraint is the invariant the whole system protects.

### 3.2 The buyer's journey

```
Browser ──JWT──► edge-gateway ──► admission-service   (join queue, SSE position updates)
                                       │  releases N/s, mints admission token (sub, eventId, 5-min TTL)
                                       ▼
Browser ──JWT + admission token──► edge-gateway ──► inventory-service  POST /holds
                                                        │  Redis Lua pre-check → Postgres conditional UPDATE
                                                        ▼  hold {id, fencingToken, expiresAt}
Browser ──JWT + Idempotency-Key──► edge-gateway ──► order-service      POST /orders  (holdId)
                                                        │  saga: CREATED → PAYMENT_PENDING → CONFIRMED | CANCELLED | FAILED
                                                        ├──► psp-simulator  (authorize/capture; webhooks back, HMAC-signed)
                                                        └──► outbox → Kafka  order.confirmed | order.cancelled
inventory-service    ◄── order.confirmed ── confirm hold → SOLD, insert ticket rows (unique constraint)
inventory-service    ◄── order.cancelled ── release hold → AVAILABLE
notification-service ◄── ticket.issued   ── email via Mailpit (idempotent on ticketId)
```

Every cross-service state change flows **outbox → Kafka**. Synchronous calls exist only where the caller needs the answer now (hold placement, order creation, PSP authorize).

---

## 4. Inventory: holds that never overlap

**Goal:** for any seat, at most one `ACTIVE` hold at a time and at most one ticket ever. Proven by a concurrency test and a property test, and measured by `oversell_total`.

### 4.1 The two-tier check

1. **Redis pre-check (fast path, not authoritative).** A Lua script runs atomically per request: for each requested seat key `seat:{eventId}:{seatId}`, if any is already held, return the conflicting seats; otherwise `SET … NX PX <ttl>` all of them and return OK. This rejects the vast majority of losing requests in microseconds without touching Postgres. Hot-key mitigation: keys are per seat, not per event, so contention spreads across the cluster's hash slots.
2. **Postgres conditional update (source of truth).** In one transaction: `UPDATE seat SET status = 'HELD', version = version + 1 WHERE id = ANY(:ids) AND event_id = :event AND status = 'AVAILABLE'`, assert the row count equals the number of seats requested, insert the `hold` row, and write the outbox event. If the count is short, roll back and release the Redis keys. The database wins every disagreement.
3. **Last line: unique constraint.** `ticket(event_id, seat_id)` is unique. Even if both tiers above were bypassed by a bug, a second ticket for the same seat fails to insert, the saga compensates, and `oversell_total` increments so the bug is *seen*.

### 4.2 Fencing tokens and expiry

Every hold carries a monotonically increasing **fencing token** (a Postgres sequence). Confirm and release commands must present the token; a command with a stale token (for a hold that expired and whose seat was re-held by someone else) is rejected. The **expiry sweeper** runs on a short interval, selects `ACTIVE` holds with `expires_at < now()` using `FOR UPDATE SKIP LOCKED`, marks them `EXPIRED`, restores the seats, deletes the Redis keys, and publishes `hold.expired`. The Redis key TTL is set slightly *longer* than the Postgres TTL so the database always decides first.

### 4.3 Per-user limits

A user may hold at most six seats per event across active holds. Enforced in the application layer of inventory, with a partial index to make the check cheap.

### 4.4 Invariants (tested as properties with jqwik)

- For any sequence of `hold`, `confirm`, `release`, `expire` commands, no seat has two `ACTIVE` holds.
- `confirm` after `expire` for the same hold is a rejected command, never a resurrection.
- Every `EXPIRED` or `RELEASED` hold leaves its seats `AVAILABLE`.
- The sum of `AVAILABLE + HELD + SOLD` seats is constant for an event.

---

## 5. Admission: the virtual waiting room

**Goal:** turn a spike into a queue; release buyers at the rate inventory can sustain; make queue positions unforgeable and non-transferable.

- **Queue:** one Redis sorted set per event, `queue:{eventId}`, scored by a server-assigned arrival timestamp (monotonic per node, Lua-side tiebreak). Joining is idempotent per `(sub, eventId)`.
- **Release:** a scheduler pops the lowest-scored N members every tick, where N is derived from a configurable target (default: inventory's measured hold p99 and a concurrency budget), and mints an **admission token** for each: a compact signed JWT `{sub, eventId, jti, exp = now + 5 min}` signed with a key only admission holds. The gateway and inventory verify the signature and the `(sub, eventId)` binding against the caller's identity token. Replay is bounded by `exp`; a used `jti` is recorded in Redis for the token's lifetime.
- **Position updates:** an SSE stream per user with position and estimated wait; the UI never polls.
- **Load shedding:** if the queue exceeds a configured maximum, joins are rejected with `503` and `Retry-After`. If inventory's error rate crosses a threshold, the release rate is halved (circuit-breaker style) and recovers gradually.
- **Fairness definition:** within one event, admission order equals arrival order. Verified by a test that enqueues 10,000 synthetic users with known arrival times and asserts the release order.

---

## 6. Order: the checkout saga

**Goal:** every order reaches a terminal state; no double charge; no lost seat; no ticket without payment.

### 6.1 State machine

```
CREATED ──authorize──► PAYMENT_PENDING ──webhook: captured──► CONFIRMED ──► (ticket.issued)
   │                        │  webhook: declined / deadline exceeded
   │                        └────────────────────────────────► CANCELLED ──► (hold released)
   └── hold invalid / PSP unreachable after retries ──────────► FAILED     ──► (hold released, refund if captured)
```

Transitions are the only way to change `state`; they are guarded, persisted with `updated_at`, and each writes exactly one outbox row in the same transaction. The orchestrator is the order service itself (orchestration, not choreography — see `docs/adr/0003`).

### 6.2 Idempotency

- **Inbound:** `POST /orders` requires an `Idempotency-Key` header. The key, request hash, and response are stored; a replay with the same key returns the stored response, a replay with a different body returns `422`. Keys expire after 24 h.
- **Outbound:** every PSP call carries an idempotency key derived from `(orderId, purpose)` so a retried `authorize` cannot create two authorizations.
- **Webhooks:** the PSP signs each webhook (HMAC-SHA256 over timestamp + body); the service rejects signatures older than 5 minutes and processes each webhook `eventId` at most once via a `processed_webhook` table with a unique key.
- **Consumers:** every Kafka consumer is idempotent on the event's `id`; the dedupe record is written in the same transaction as the effect.

### 6.3 Timeouts, retries, and the DLQ

- PSP calls: 2 s timeout, 3 retries with jittered exponential backoff, circuit breaker (Resilience4j). Exhausted → `FAILED` with compensation.
- Saga deadline: an order in `PAYMENT_PENDING` for longer than the hold TTL is moved to `CANCELLED` by a deadline sweeper. A webhook arriving after that is recorded and, if it says *captured*, triggers a **refund** compensation. `saga_stuck` counts orders past deadline that the sweeper could not resolve.
- Kafka consumers: retry with backoff on transient errors, then dead-letter to `<topic>.DLQ` with the original headers and the failure reason. DLQ depth is a paged alert.

### 6.4 Transactional outbox

Every service that publishes owns an `outbox` table `{id, aggregate_type, aggregate_id, type, payload, created_at, published_at}`. A relay (a polling publisher first; Debezium via Kafka Connect as a documented upgrade) reads unpublished rows in order and publishes to Kafka with the aggregate id as the partition key, so per-aggregate ordering is preserved. The relay is at-least-once; consumers dedupe.

---

## 7. Security — authentication and authorisation

**Authn**
- **Keycloak** is the OIDC provider; the realm is exported as JSON and committed; it runs in compose and in kind. The seam is standard OIDC so Cognito can replace it on AWS with a config change.
- **React UI:** Authorization Code + PKCE, short-lived access tokens, silent refresh. No BFF.
- **Service-to-service:** OAuth2 client credentials with narrow scopes (`inventory:hold`, `inventory:confirm`, `order:write`, `admission:mint`). No service calls another with a user's token.
- **Zero trust inside the cluster:** every service is a Spring Security resource server and validates the JWT itself. The gateway validating first is defence in depth, not a reason to trust internal traffic. mTLS via Linkerd is a stretch goal.
- **PSP webhooks:** HMAC signature, timestamp replay window, idempotent processing (§6.2).

**Authz**
- **Roles (RBAC):** `CUSTOMER`, `ORGANIZER`, `ADMIN`. **Ownership (ABAC):** a customer sees and cancels only their own holds and orders; an organizer manages only their own events. Enforced with `@PreAuthorize` and custom permission evaluators in the **application layer**, not in controllers, so the same rules hold for every transport.
- **Admission token as a capability:** signed, ~5-minute TTL, bound to `(sub, eventId)`, single-use per `jti`. Checked at the gateway *and* by inventory. A queue position cannot be shared, sold, or replayed.
- **Per-user hold limits** (§4.3) and gateway **per-user token-bucket rate limits** (Redis) cap what one account can do per second.
- **Kafka:** SASL/SCRAM with per-service topic ACLs locally (Strimzi `KafkaUser`); MSK IAM auth on AWS.
- **Secrets:** never in the repo. Kubernetes `Secret`s populated by External Secrets Operator from AWS Secrets Manager; IRSA for pod AWS access; `.env.example` documents local values.

**Tested like everything else:** per-endpoint 401/403 matrix tests, IDOR tests (user A cannot read or cancel user B's order), admission-token tamper/expiry/cross-user/replay tests, webhook signature and replay-window tests, and an integration test against a real Keycloak via Testcontainers.

---

## 8. Events and contracts

Topics (Avro, schema registry, backward-compatible evolution only):

| Topic | Producer | Consumers | Key |
|---|---|---|---|
| `hold.placed`, `hold.expired`, `hold.released` | inventory | order, admission (capacity feedback) | `holdId` |
| `order.confirmed`, `order.cancelled`, `order.failed` | order | inventory | `orderId` |
| `ticket.issued` | inventory | notification | `ticketId` |
| `admission.granted` | admission | order (audit), metrics | `sub` |

Every event carries `{id (UUID), type, occurredAt, traceparent, aggregateId, version}` in headers. **Contracts:** consumer-driven contracts (Pact) for HTTP APIs; schema-registry compatibility checks for events; both run in `make check`.

---

## 9. Architecture

```
┌──────────────┐   HTTPS + JWT    ┌──────────────────┐    ┌────────────────────┐
│  React demo  │ ───────────────► │  edge-gateway    │ ─► │ admission-service  │──┐ Redis (zset queue,
│  UI (thin)   │ ◄─── SSE ─────── │  (Spring Cloud   │    │ waiting room, SSE, │  │ token jti, rate
│              │                  │   Gateway)       │    │ mints admission tok│  │ limits, seat locks)
└──────────────┘                  │  • JWT validate  │    └────────────────────┘  │
                                  │  • rate limit    │    ┌────────────────────┐  │
                                  │  • admission chk │ ─► │ inventory-service  │◄─┘
                                  └──────────────────┘    │ seats, holds, TTL, │──► Postgres (truth)
                                            │             │ confirm, sweeper   │
                                            │             └─────────┬──────────┘
                                            │                       │ outbox → Kafka
                                            ▼                       ▼
                                  ┌──────────────────┐    ┌────────────────────┐    ┌───────────────┐
                                  │ order-service    │◄──►│ Kafka (Strimzi)    │───►│ notification  │
                                  │ saga orchestrator│    │ + schema registry  │    │ (Mailpit)     │
                                  │ outbox, idempot. │    └────────────────────┘    └───────────────┘
                                  └────────┬─────────┘
                                           │ authorize / capture           webhooks (HMAC)
                                           ▼                                    │
                                  ┌──────────────────┐ ◄────────────────────────┘
                                  │ psp-simulator    │  fault injection: latency, 5xx, dup/late webhooks
                                  └──────────────────┘
   Cross-cutting: Keycloak (OIDC) · OpenTelemetry → Tempo · Micrometer → Prometheus → Grafana · Loki
```

**Stack:** Java 25 with virtual threads · Spring Boot 4.x · Gradle Kotlin DSL multi-module monorepo · Postgres · Redis · Kafka (Strimzi on kind, MSK on AWS) with schema registry and Avro · Keycloak · OpenTelemetry · Micrometer.

**Shared libraries (`libs/`):** `platform-bom` (one version catalogue), `observability-starter` (metrics, tracing, structured logs, correlation ids, `/healthz` `/readyz`), `outbox-starter` (outbox table, relay, publisher), `idempotency-starter` (inbound key filter, consumer dedupe), `test-fixtures` (Testcontainers wiring, JWT fixtures, Kafka test helpers).

**Per-service layout (hexagonal, enforced by ArchUnit):** `domain/` (entities, value objects, invariants — no Spring), `application/` (use cases, ports, authorisation), `adapters/in/` (REST, Kafka consumers), `adapters/out/` (JPA, Redis, Kafka producers, PSP client). `domain` and `application` MUST NOT depend on `adapters` or on Spring Web/Data types.

---

## 10. Observability

- **Metrics (Micrometer → Prometheus):** business — `oversell_total` (MUST stay 0), `holds_active`, `holds_expired_total`, `holds_placed_total`, `admission_rate`, `queue_depth`, `admission_wait_seconds`, `saga_duration_seconds` by terminal state, `saga_stuck`, `psp_call_seconds` by outcome, `outbox_lag_seconds`, `consumer_lag`. Plus JVM, HTTP server, Kafka client, and connection-pool defaults.
- **Traces (OpenTelemetry → Tempo):** one trace from gateway through admission, hold, order, PSP, Kafka (context propagated in headers), to notification. Trace ids in every log line.
- **Logs (→ Loki):** structured JSON, correlation id, no PII beyond user id, never a token or secret.
- **Dashboards and alerts as code:** `ops/grafana/` dashboards (Sale Overview, Inventory, Saga, Kafka) and `ops/prometheus/rules/` with multi-window, multi-burn-rate SLO alerts.
- **SLOs:** hold p99 < 150 ms at target RPS; admission-to-hold success ≥ 99.9 %; saga terminal within hold TTL + 30 s ≥ 99.9 %; `oversell_total == 0` (any increment pages).

---

## 11. Testing and TDD

Red-green-refactor per slice, visible in the commit history. Layers:

| Layer | Scope | Tooling |
|---|---|---|
| Unit | domain invariants, state machines, token signing/verification, rate-limit maths, authz rules | JUnit 5, AssertJ, Mockito |
| Property | inventory invariants (§4.4); the saga terminates for any interleaving | jqwik |
| Concurrency | N threads race one seat against real Postgres + Redis; exactly one wins | JUnit + Testcontainers |
| Architecture | hexagonal boundaries, no Spring in `domain` | ArchUnit |
| Integration | repositories, outbox relay, Kafka consumers, Keycloak resource-server config | Testcontainers (Postgres, Redis, Kafka, Keycloak) |
| Contract | HTTP consumer-driven; event schema compatibility | Pact, schema registry check |
| Security | 401/403 matrix, IDOR, token tamper/replay, webhook signature | Spring Security test + fixtures |
| Mutation | domain modules must survive a mutation score threshold | PIT (nightly) |
| Load and chaos | k6 spike 100k VUs / 5k seats; Toxiproxy on PSP and Postgres | k6, Toxiproxy (nightly, pre-release) |

Coverage gate (JaCoCo) on `domain` and `application` packages; do not chase coverage on adapters.

---

## 12. Platform

- **Docker:** Jib or buildpacks layered images, distroless, non-root, `HEALTHCHECK`; `compose.yaml` for the dev loop (Postgres, Redis, Kafka + schema registry, Keycloak, Mailpit, Prometheus, Grafana, Tempo, Loki, psp-simulator, all services).
- **Kubernetes:** a Helm chart per service plus an umbrella chart; kind locally; Strimzi Kafka; liveness/readiness probes; PodDisruptionBudgets; NetworkPolicies default-deny; resource requests and limits; HPA on CPU and **KEDA** on Kafka lag (consumers) and Redis queue depth (admission).
- **AWS (demo only):** Terraform modules for VPC, EKS, RDS Postgres, ElastiCache Redis, MSK, ECR, IRSA, Secrets Manager. `terraform validate`, `tflint`, and `plan` run in CI; `make aws-up` / `make aws-down` exist for a recorded demo and nothing else.
- **CI (GitHub Actions):** build → unit + property + ArchUnit → integration (Testcontainers) → contract → image build → Trivy scan → kind e2e smoke. Nightly: mutation, load, chaos.

---

## 13. Build order (thin vertical slice first)

Each number is its own spec in `specs/`, then a plan, then a TDD implementation.

- **`000` Scaffold:** Gradle multi-module, libs skeleton, compose infra, Keycloak realm + resource-server baseline, CI gates, health/metrics, ArchUnit.
- **`001` Inventory:** seat model, hold/release/confirm, TTL sweeper, fencing tokens, per-user limits, ownership authz; concurrency and property proofs of no-oversell.
- **`002` Order saga:** PSP simulator with fault injection, saga state machine, inbound/outbound idempotency, outbox relay, webhook verification, deadline sweeper, DLQ.
- **`003` Admission:** waiting room, release scheduler, signed admission tokens, SSE, gateway with JWT + rate limit + token check.
- **`004` Observability:** dashboards, traces end to end, SLO burn-rate alerts, reconciliation job.
- **`005` Kubernetes:** Helm, kind, Strimzi, KEDA/HPA, NetworkPolicies, Kafka ACLs, External Secrets, Trivy and dependency-check gates.
- **`006` Load and chaos:** k6 scenarios, Toxiproxy; publish results in `docs/results/` and the README.
- **`007` AWS:** Terraform modules and the one-shot demo deploy.
- **`008` React demo UI:** waiting room, seat map, checkout.
- **`009` Notification service:** idempotent consumer, email via Mailpit.
- **Stretch:** CQRS read model for the seat map, multi-region notes, Linkerd mTLS, ADRs for every remaining trade-off.

---

## 14. Gaps and what a senior/staff reviewer will expect

Address these explicitly in the repo and README — they separate a real system from a toy:

- **Where is the source of truth, and what happens when the cache disagrees?** Postgres. Redis is an optimisation; every path is correct with Redis flushed (slower, not wrong). Tested.
- **The delayed-confirm-after-expiry race.** Fencing tokens (§4.2). Tested.
- **Dual writes.** Transactional outbox everywhere; no "save then publish".
- **At-least-once everything.** Every consumer and every webhook handler is idempotent with a dedupe record in the same transaction as the effect.
- **Sagas are not transactions.** Compensations are explicit, tested, and have their own metric; a refund is a first-class step.
- **Hot keys and partitioning.** Per-seat Redis keys; Kafka partitioned by aggregate id; Postgres row-level contention bounded by the pre-check.
- **Backpressure, not just autoscaling.** The waiting room and gateway rate limits shape load *before* it reaches services; KEDA reacts to lag; the release rate adapts to inventory health.
- **Fairness is defined and tested**, not assumed.
- **Security is zero-trust inside the cluster**, with capability-style admission tokens and ownership checks in the application layer.
- **Clock skew and time.** Server-assigned timestamps; token `exp` with leeway; TTL decided by Postgres, hinted by Redis.
- **Schema evolution.** Avro with backward-compatibility checks in CI; Flyway migrations expand-then-contract.
- **Observability proves the invariants** during the load test, and the results are committed.
- **Cost.** Local-first; AWS is a recorded demo with `make aws-down` as part of the script.
- **Deliberately out of scope (documented):** dynamic pricing, resale/transfer, multi-currency settlement, real PSP integration, multi-region active-active.

---

## 15. How this is useful (interviews, learning)

- **System design interviews:** "design Ticketmaster" is a canonical question. Sellout is that answer, built and measured: waiting room, hot-row contention, sagas, outbox, idempotency, KEDA, SLOs. Every trade-off has an ADR.
- **Depth questions:** the concurrency test, the fencing-token race, and the duplicate-webhook path are concrete stories with commits behind them.
- **Learning:** forces mastery of Spring Boot 4 / Java 25 virtual threads, Kafka with Strimzi and schema registry, Testcontainers-driven TDD, Helm/KEDA on kind, Terraform for EKS/RDS/MSK, and Prometheus/Grafana SLO alerting.
- **Portfolio shape:** the JVM large-scale-systems flagship next to the AI-agent flagships: range plus depth.
