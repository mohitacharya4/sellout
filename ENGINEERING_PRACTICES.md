# Sellout — Engineering Practices (Testing, Docker, CI/CD, Ops)

Companion to `ENGINEERING_CONSTITUTION.md`. The constitution says *what must be true*; this doc says *how* — the concrete testing, containerization, CI/CD, and operational standards. **Claude Code and contributors MUST follow these; the scaffold (spec `000`) sets most of them up once.** RFC-2119 keywords apply.

---

## 1. Testing strategy (the pyramid plus the distributed-systems-specific parts)

### 1.1 The layers

| Layer | Scope | Tooling | Runs |
|---|---|---|---|
| **Unit** | `domain` + `application`: invariants, state machines, token signing, rate-limit maths, authz rules — in-memory ports, no Spring context | JUnit 5, AssertJ, Mockito | every commit, seconds |
| **Property** | SPEC §4.4 invariants; saga termination under arbitrary interleavings | jqwik | every commit |
| **Architecture** | Hexagonal boundaries (Constitution §2.1); no Spring in `domain`; no cycles | ArchUnit | every commit |
| **Contract** | HTTP consumer-driven contracts between services and the UI; Avro schema compatibility | Pact JVM, schema-registry maven/gradle check | every commit |
| **Integration** | Real Postgres, Redis, Kafka, Keycloak via **Testcontainers**: repositories, Lua scripts, outbox relay, consumers, resource-server config | JUnit 5 + Testcontainers + `@SpringBootTest` | every PR |
| **Concurrency** | N threads race one seat / one order against real infra; exactly one wins | JUnit 5 + Testcontainers + `ExecutorService` / virtual threads | every PR |
| **Security** | 401/403 matrix per endpoint, IDOR, admission-token tamper/expiry/replay, webhook HMAC + replay window | Spring Security Test, `test-fixtures` JWT builders | every commit |
| **E2E smoke** | Full journey on kind: join queue → admitted → hold → pay → ticket → email in Mailpit | k6 (functional scenario) + `kubectl` | PR (kind) |
| **Mutation** | `domain` modules survive a mutation score ≥ 80 % | PIT | nightly |
| **Load / spike** | 100k VUs / 5k seats; asserts `oversell_total == 0`, hold p99, saga terminal rate | k6 | nightly, pre-release |
| **Chaos** | PSP latency/5xx, Postgres partition, Kafka broker loss; asserts invariants hold and the system recovers | Toxiproxy, `kubectl delete pod` | nightly |

**Coverage:** JaCoCo threshold (≥ 85 %) on `domain` and `application` packages only. Do **not** chase coverage on adapters and configuration.

### 1.2 Determinism

- **Time is injected.** Every component that reads the clock takes a `java.time.Clock`. Tests use a fixed or stepping clock; no `Thread.sleep` to "wait for expiry".
- **Ids are injected.** UUID and fencing-token generation go through a port so tests can assert exact values.
- **No test hits the network** or a running compose stack. Testcontainers only, with reuse enabled locally for speed.
- **Kafka tests use a real broker** (Testcontainers) — embedded Kafka behaves differently under rebalance.

### 1.3 Distributed-systems tests that are easy to forget (build these)

- **Race one seat.** 200 threads `POST /holds` for the same seat; assert exactly one `201`, the rest `409`, Postgres shows one `ACTIVE` hold, `oversell_total == 0`. Repeat with Redis flushed mid-test.
- **Late confirm after expiry.** Place a hold, advance the clock past TTL, run the sweeper, re-hold the seat as another user, then confirm with the *original* fencing token → rejected; the second user's hold is intact.
- **Duplicate webhook.** Deliver the same `captured` webhook twice and out of order with `declined`; assert one `CONFIRMED` transition, one ticket, one outbox row.
- **Idempotent replay.** `POST /orders` twice with the same `Idempotency-Key` → identical response, one order; with a different body → `422`.
- **Consumer redelivery.** Deliver `order.confirmed` twice; assert one ticket row and one `ticket.issued` event.
- **Outbox crash.** Kill the relay after publish but before marking published; on restart the event is republished and the consumer dedupes.
- **Saga deadline.** Order stuck in `PAYMENT_PENDING`; advance clock; sweeper cancels; late `captured` webhook → refund compensation recorded.
- **Fairness.** Enqueue 10,000 users with known arrival order; assert release order matches.
- **Token binding.** Admission token minted for user A used by user B → `403`; expired → `401`; reused `jti` → `403`; tampered signature → `401`.
- **Migration round trip.** Flyway migrate on a clean Testcontainers Postgres; assert the schema matches JPA metadata (`ddl-auto=validate`).

### 1.4 Test data and fixtures

`libs/test-fixtures` provides: Testcontainers singletons (Postgres, Redis, Kafka, Keycloak) with reuse; JWT builders for each role and for admission tokens; a seeded demo event (one venue, 5,000 seats, three sections); deterministic factories. The same seed powers integration tests, the k6 scenarios, and the demo.

---

## 2. Docker and containerization

### 2.1 Images

- **Jib** builds layered images per service (dependencies, resources, classes as separate layers) with **no Dockerfile** for Java services, `psp-simulator` included; the `ui` MAY use a multi-stage Dockerfile.
- **Base image:** distroless Java 25, pinned by digest. Final images contain no shell, no build tooling.
- **Run as non-root** (`USER 65532`), read-only root filesystem, `--tmpdir` for the JVM; no secrets in `ENV` or layers.
- **JVM flags** are set once in the Jib config: container-aware heap, virtual threads enabled, `-XX:+ExitOnOutOfMemoryError`.
- **Health checks without a shell.** Distroless has no `curl`; each image ships a tiny `HealthCheck` main class that GETs `/healthz` and exits non-zero on failure. Compose runs it (`test: ["CMD", "java", "-cp", "/app/classes", "dev.sellout.platform.HealthCheck"]`); Kubernetes uses HTTP probes directly.

### 2.2 Runtime behaviour

- **Fail fast on config.** `@ConfigurationProperties` records with `@Validated`; a missing required property fails boot with a clear message.
- **Migrations before serving.** Flyway runs on boot; the readiness probe stays red until migrations complete.
- **Graceful shutdown:** `server.shutdown=graceful`, Kafka consumers stop polling and commit, the outbox relay finishes its batch, then exit. `terminationGracePeriodSeconds` ≥ 30.

### 2.3 Compose (dev) and parity

- **`compose.yaml`** brings up the full stack: `postgres`, `redis`, `kafka` (KRaft) + `schema-registry`, `keycloak` (realm import from `ops/keycloak/`), `mailpit`, `prometheus`, `grafana`, `tempo`, `loki`, `psp-simulator`, and every service.
- **`depends_on: condition: service_healthy`** with real health checks. No `sleep`.
- **Profiles:** `infra` (backing services only, for running services from the IDE) and `full`.
- **Named volumes** for Postgres and Kafka; resource limits so the stack fits a laptop.

### 2.4 Supply chain

- **Trivy** scans every image in CI; HIGH/CRITICAL fails the build.
- **OWASP dependency-check** on the Gradle dependency graph; CVSS ≥ 7 fails.
- **SBOM** (CycloneDX Gradle plugin) per service, attached to the release.
- **Gradle dependency verification** (`gradle/verification-metadata.xml`) for checksums.

---

## 3. CI/CD

### 3.1 Pipeline (GitHub Actions)

```
PR:      compile ─► spotless+checkstyle ─► unit+property+archunit ─► contract ─► integration(testcontainers)
         ─► jib build ─► trivy ─► helm lint + kubeconform ─► terraform validate + tflint ─► kind e2e smoke
nightly: mutation (PIT) ─► k6 spike ─► chaos (toxiproxy) ─► publish results to docs/results/ (PR)
release: all gates green ─► tag (semver) ─► push images to GHCR ─► helm package
```

- **Gradle build cache + configuration cache** and the Testcontainers image cache are persisted between runs.
- **Jobs run in parallel** per module where the dependency graph allows.
- **A red gate blocks merge** (Constitution §5). No manual overrides.

### 3.2 Local mirror

`make check` runs the same PR gates (minus kind e2e and image scan); `make test-int` runs the Testcontainers suites; `make e2e` runs the kind smoke locally. A pre-commit hook runs Spotless and the unit suite.

### 3.3 Security in CI

- **Secret scanning** (gitleaks) on every push.
- **SAST:** Error Prone with security checks; Semgrep Java rules.
- **Dependency updates:** Renovate with grouped, auto-tested PRs.

---

## 4. Dependencies, config, and versioning

- **One version catalogue:** `gradle/libs.versions.toml`. Modules reference aliases, never literal versions. `libs/platform-bom` aligns transitive versions.
- **Convention plugins** in `build-logic/` for `sellout.java-library`, `sellout.spring-service`, `sellout.spring-library` so every module gets the same compiler flags, test config, and quality plugins.
- **Config via `@ConfigurationProperties`** records, validated at boot. No `@Value` scattered in code, no `System.getenv`.
- **Secrets** from env or a secret manager only; `.env.example` documents every required variable.
- **Semantic versioning** + **Conventional Commits**; small, narrative commits; never a `Co-Authored-By` line.

---

## 5. Observability and operations

- **Endpoints:** `/healthz`, `/readyz`, `/actuator/prometheus` on every service (from `observability-starter`).
- **Metrics naming:** business metrics use the names in SPEC §10 exactly; labels are low-cardinality (`event_id` is allowed only on the demo scale and is documented as such).
- **Traces:** OpenTelemetry Java agent or Micrometer Tracing bridge; W3C `traceparent` propagated in HTTP and Kafka headers; sampling 100 % locally, head-based 10 % on AWS.
- **SLOs and alerts** (SPEC §10) live in `ops/prometheus/rules/` with multi-window, multi-burn-rate alerts; Grafana dashboards in `ops/grafana/` are provisioned, not clicked together.
- **Runbook:** `docs/RUNBOOK.md` — drain a consumer, replay a DLQ, force-release a hold, reconcile tickets against orders, rotate the admission signing key, restore Postgres.
- **Reconciliation job** (`load/reconcile`) runs after every load test and on demand: seats with more than one ticket, tickets without a `CONFIRMED` order, `ACTIVE` holds past TTL, orders past deadline. Its report is committed with the load-test results.

---

## 6. Definition of Done — additions to Constitution §9

A feature is also not done unless: it ships with the test layers relevant to it (including the §1.3 case that applies); its image builds, scans clean, and passes probes on kind; config is typed and validated; new dependencies are in the catalogue and pass dependency-check; any new operational surface has a metric, a dashboard panel, an alert if it can page, and a runbook note; and any non-obvious trade-off has an ADR in `docs/adr/`.
