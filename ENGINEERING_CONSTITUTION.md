# Sellout — Engineering Constitution

The non-negotiable standards for this codebase. **Every contributor and every coding agent (Claude Code included) MUST load this file before writing code and MUST NOT violate it.** If a rule blocks you, change the rule via PR — don't bypass it.

Keywords **MUST**, **MUST NOT**, **SHOULD**, **MAY** follow RFC 2119.

---

## 0. How to use this

1. Read this constitution **and `ENGINEERING_PRACTICES.md`** (the concrete testing/Docker/CI/ops standards that implement these rules).
2. Read `PROBLEM.md` and `SPEC.md`, then the relevant feature spec in `specs/`.
3. Implement only what the spec defines. No scope creep, no speculative abstraction.
4. All gates in §5 MUST pass locally (`make check`) before any commit.
5. CI re-runs every gate; a red gate blocks merge. No exceptions, no manual overrides.

---

## 1. First principles

1. **Production-grade or not at all.** No throwaway or demo-quality code paths in `main`. If it ships, it is observable, tested, recoverable, and secure.
2. **Correctness under contention is the product.** One seat, one buyer, one charge. Any change that touches holds, confirms, payments, or events MUST keep the invariants in SPEC §4.4 and §6 provable by test.
3. **12-factor.** Config in env, stateless processes, explicit dependencies, dev/prod parity, logs as event streams, disposability, backing services attached via URL.
4. **Simplicity first (YAGNI).** Build what the spec needs. No frameworks or abstractions for hypothetical futures. Delete dead code immediately.
5. **Boring tech, on purpose.** Proven tools over novel ones. The novelty budget is spent on the contention model, the saga, and the waiting room — nowhere else.
6. **Postgres is the source of truth.** Redis, Kafka, and caches are optimisations or transport. Every path MUST be *correct* (slower is fine) with Redis flushed.
7. **At-least-once delivery, exactly-once effects.** Every consumer, webhook handler, and retried call is idempotent, and its dedupe record is written in the same transaction as its effect.
8. **Fail loud, recover gracefully.** Validate inputs at boundaries; never swallow exceptions silently. Every external call has a timeout, a retry policy, and a defined failure outcome.
9. **Security is a precondition.** Zero trust inside the cluster; every service validates its own tokens; ownership is checked in the application layer; secrets never touch the repo.
10. **Measured, not asserted.** A claim in the README ("0 oversells", "p99 < 150 ms") MUST have a committed metric, a load test, and a result file behind it.

---

## 2. Repository structure (monorepo)

```
sellout/                          # = the repo root
├── ENGINEERING_CONSTITUTION.md   # this file
├── ENGINEERING_PRACTICES.md      # testing / Docker / CI / ops standards
├── PROBLEM.md  SPEC.md           # product + architecture
├── specs/                        # feature specs (spec-driven)
├── docs/adr/                     # architecture decision records
├── docs/results/                 # committed load-test results + dashboard screenshots
├── Makefile                      # single entrypoint for all dev commands
├── settings.gradle.kts           # Gradle multi-module root (Kotlin DSL)
├── gradle/libs.versions.toml     # the ONE version catalogue
├── compose.yaml                  # full local stack (infra + services)
├── services/
│   ├── edge-gateway/             # Spring Cloud Gateway: JWT, rate limit, admission check
│   ├── admission-service/        # waiting room, release scheduler, admission tokens, SSE
│   ├── inventory-service/        # seats, holds, TTL sweeper, confirm/release, tickets
│   ├── order-service/            # checkout saga, idempotency, PSP client, webhooks
│   ├── notification-service/     # ticket.issued consumer → email
│   └── psp-simulator/            # fake payment provider with fault injection
├── libs/
│   ├── platform-bom/             # dependency alignment
│   ├── observability-starter/    # metrics, tracing, JSON logs, health endpoints
│   ├── outbox-starter/           # outbox table, relay, publisher
│   ├── idempotency-starter/      # inbound key filter, consumer dedupe
│   └── test-fixtures/            # Testcontainers, JWT fixtures, Kafka helpers
├── ops/                          # grafana dashboards, prometheus rules, keycloak realm
├── deploy/
│   ├── helm/                     # one chart per service + umbrella
│   ├── kind/                     # local cluster config, Strimzi, KEDA
│   └── terraform/                # AWS: vpc, eks, rds, elasticache, msk, ecr, irsa
├── load/                         # k6 scenarios, Toxiproxy configs, reconciliation job
├── ui/                           # thin React demo UI
└── .github/workflows/            # ci.yml, nightly.yml
```

**Rules:** one concern per module; no file over ~400 lines (split it); no circular dependencies between Gradle modules. Every service follows the hexagonal layout in §2.1 and is enforced by ArchUnit.

### 2.1 Hexagonal layout per service (enforced)

```
services/<name>/src/main/java/dev/sellout/<name>/
├── domain/        # entities, value objects, invariants, domain events — NO Spring, NO JPA annotations
├── application/   # use cases (ports in), ports out, authorisation rules, sagas
├── adapters/in/   # REST controllers, Kafka consumers, schedulers
└── adapters/out/  # JPA repositories, Redis, Kafka producers, HTTP clients
```

- `domain` MUST NOT import `org.springframework.*`, `jakarta.persistence.*`, or anything from `adapters`.
- `application` MAY use Spring's `@Transactional` and `@PreAuthorize` and MUST NOT import `adapters` or Spring Web/Data types.
- `adapters` depend inward only. A use case MUST be runnable in a unit test with in-memory ports.

---

## 3. Contention, consistency, and event rules

1. **Two-tier seat check, DB decides.** A hold is granted only when the Postgres conditional update affects exactly the requested rows. Redis pre-checks MAY reject early; they MUST NOT grant.
2. **Fencing tokens on every mutating hold command.** Confirm and release MUST present the hold's fencing token and MUST be rejected if it is stale.
3. **TTL is decided by Postgres.** The sweeper, not Redis expiry, releases holds. Redis TTLs MUST be longer than the Postgres TTL.
4. **The last line of defence is a constraint.** `ticket(event_id, seat_id)` MUST be unique. A violation MUST increment `oversell_total` and MUST trigger compensation.
5. **No dual writes.** A state change and its event MUST be committed in the same transaction via the outbox. Publishing directly to Kafka from a request path is forbidden.
6. **Explicit state machines.** Saga and hold states change only through named transitions with guards. No `setState()`.
7. **Every side effect has an idempotency key** — inbound requests, outbound PSP calls, webhook handling, Kafka consumption.
8. **Loops and retries terminate.** Every retry has a cap, a backoff, and a terminal outcome (`FAILED`, DLQ). Every saga has a deadline and a sweeper.
9. **Ordering is per aggregate.** Kafka messages are keyed by aggregate id. Nothing assumes global ordering.
10. **Schema changes are backward compatible.** Avro schemas MUST pass the registry compatibility check; Flyway migrations follow expand-then-contract.

---

## 4. Security rules

1. **Zero trust.** Every service validates the JWT (issuer, audience, signature, expiry) itself. "It came through the gateway" is not authorisation.
2. **Authorisation lives in the application layer** (`@PreAuthorize` + permission evaluators). Controllers MUST NOT contain ownership logic.
3. **Ownership is enforced**: a `CUSTOMER` accesses only their own holds and orders; an `ORGANIZER` only their own events. Every endpoint that takes an id MUST have an IDOR test.
4. **Admission tokens are capabilities**: signed, short-lived, bound to `(sub, eventId)`, single-use per `jti`, verified by the gateway *and* inventory.
5. **Service-to-service calls use client credentials with narrow scopes.** Never forward a user token between services.
6. **Webhooks are signature-verified** with a timestamp replay window and processed idempotently.
7. **Secrets never enter the repo, logs, traces, or images.** Local: `.env` (gitignored). Cluster: External Secrets Operator. AWS: Secrets Manager + IRSA.
8. **Rate limits at the edge** per user; **hold limits** per user per event in inventory.
9. **Dependencies and images are scanned** (OWASP dependency-check, Trivy); HIGH/CRITICAL blocks merge.

---

## 5. Quality gates (`make check`, enforced in CI)

`make check` MUST run and pass all of: **compile** (`-Werror`) · **lint/format** (Spotless with google-java-format, Checkstyle) · **static analysis** (Error Prone) · **unit + property tests** · **ArchUnit** · **contract tests** · **coverage threshold** (JaCoCo on `domain` + `application`). A commit MUST NOT be pushed with a red gate. `make test-int` (Testcontainers) runs on every PR in CI.

---

## 6. Testing rules

1. **TDD is the workflow.** Red → green → refactor, with the red commit visible. A feature's first commit is its failing test.
2. **Domain and application logic is unit-tested** with in-memory ports; no Spring context.
3. **Invariants are property-tested** (jqwik): no overlapping holds; conservation of seats; every saga terminates.
4. **Contention is tested with real infrastructure**: N threads racing one seat against Testcontainers Postgres + Redis; exactly one hold wins, every time.
5. **Every consumer and webhook handler has a duplicate-delivery test** and an out-of-order test.
6. **Every endpoint has a 401/403 matrix test**; every id-taking endpoint has an IDOR test.
7. **No test depends on a running compose stack or the network.** Testcontainers only.
8. **Load and chaos tests are code** in `load/` and run nightly; their results are committed to `docs/results/`.

---

## 7. Observability rules

1. **Every service exposes** `/healthz` (liveness), `/readyz` (dependencies reachable), and `/actuator/prometheus`.
2. **Business metrics are mandatory** for every slice (SPEC §10). A new state or queue without a metric is not done.
3. **One trace per buyer journey.** Trace context MUST propagate through HTTP, Kafka headers, and scheduled work that continues a saga.
4. **Structured JSON logs** with `traceId`, `spanId`, `userId` (never a token, never PII beyond the id).
5. **Dashboards and alert rules are code** in `ops/` and deployed with the stack.
6. **`oversell_total > 0` pages.** No exceptions.

---

## 8. Platform rules

1. **Images are distroless, non-root, healthchecked, and reproducible** (Jib, pinned base digests).
2. **Every workload has** requests/limits, liveness/readiness probes, a PodDisruptionBudget, and a NetworkPolicy.
3. **Autoscaling is driven by business signals** (Kafka lag, queue depth) via KEDA, with CPU as a floor.
4. **Local-first.** `make up` (compose) and `make kind-up` MUST bring up the whole system with zero cloud credentials. AWS is a one-shot, recorded demo; `make aws-down` is part of the script.
5. **Infrastructure is code and is linted** (Helm lint, kubeconform, `terraform validate`, tflint) in CI.

---

## 9. Definition of Done (per feature)

Done when: it matches its `specs/` entry · all §5 gates pass · `make test-int` green · new domain logic is unit- and property-tested · concurrency-sensitive changes have a race test · every new endpoint has its authz matrix and IDOR tests · new operational surface has a metric, a dashboard panel, and a runbook note · docs/README/ADRs updated · no `TODO` in shipped paths. If any box is unchecked, it is not done.
