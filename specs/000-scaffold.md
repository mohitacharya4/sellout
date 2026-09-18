# 000 — Project Scaffold (Gradle, libs, compose, auth baseline, CI, tooling)

**Status:** done
**Depends on:** none — **this is the very first slice**, before feature `001`.

## Goal
Stand up the skeleton so every later slice inherits build conventions, testing infrastructure, containerization, CI, config validation, security baseline, and observability "for free." No product features here — just the rails defined in `../ENGINEERING_PRACTICES.md`, plus one placeholder service that proves every rail end to end.

## In scope
- **Monorepo layout** per Constitution §2: `services/`, `libs/`, `build-logic/`, `ops/`, `deploy/`, `load/`, `ui/`, `specs/`, `docs/`.
- **Gradle Kotlin DSL:** `settings.gradle.kts` with all modules; `gradle/libs.versions.toml` as the single version catalogue; convention plugins in `build-logic/` (`sellout.java-library`, `sellout.spring-library`, `sellout.spring-service`) applying Java 25, `-Werror`, Error Prone, Spotless (google-java-format), Checkstyle, JaCoCo, JUnit 5 platform, ArchUnit, and Jib. Gradle wrapper committed; build cache + configuration cache on.
- **`libs/platform-bom`:** Spring Boot 4.x BOM import plus alignment for Kafka, Testcontainers, OpenTelemetry, Resilience4j, jqwik, Pact. — **As built:** Boot's BOM covers Kafka and OpenTelemetry; Testcontainers, testcontainers-keycloak, ArchUnit, and jqwik are pinned; Resilience4j and Pact join with the slices that first use them.
- **`libs/observability-starter`:** auto-configuration providing `/healthz`, `/readyz` (every registered Spring Boot `HealthIndicator` except liveness — plan deviation 1), `/actuator/prometheus`, structured JSON logging with `traceId`/`spanId`/`userId`, W3C trace propagation, and a `Clock` bean. Unit-tested with a slice test.
- **`libs/outbox-starter`** and **`libs/idempotency-starter`:** module skeletons with their Flyway migration scripts (`outbox`, `idempotency_key`, `processed_message` tables) and public ports defined; relay and filter logic lands in `001`/`002`. ArchUnit rules in place. — **Deferred:** both modules are created by specs 001/002 with their first real code (plan deviation 5).
- **`libs/test-fixtures`:** Testcontainers singletons for Postgres, Redis, Kafka (KRaft), Keycloak with container reuse; JWT builders for `CUSTOMER`/`ORGANIZER`/`ADMIN` and for admission tokens (signing key from a test fixture); a `FixedClock` helper; the seeded demo event (one venue, three sections, 5,000 seats) as a Flyway test migration. — **As built:** Postgres + Keycloak containers, role JWT fixtures, `MutableClock`, and shared ArchUnit rules; Redis/Kafka containers and the admission-token fixture arrive with 001–003, and the seed is the event row only, via a `demo`-profile seeder (plan deviations 2–3).
- **Auth baseline:** `ops/keycloak/realm-sellout.json` (realm, three roles, a public PKCE client for the UI, one confidential client per service with its scopes, demo users); every service module uses `spring-boot-starter-oauth2-resource-server` with issuer/audience validation; a shared security configuration in its own `libs/security-starter` (decided in the plan, deviation 4) that permits `/healthz`, `/readyz`, `/actuator/prometheus` and requires a JWT elsewhere; method security enabled. A Testcontainers Keycloak integration test proves a real token is accepted and a tampered one is rejected.
- **Placeholder service `inventory-service`:** hexagonal layout, one `GET /events/{id}` returning the seeded event from Postgres, Flyway on boot, `ddl-auto=validate`, `@ConfigurationProperties` validated at boot, graceful shutdown, virtual threads on. This is the module `001` grows into; it MUST contain no hold logic.
- **`psp-simulator`:** module skeleton with a health endpoint only (fault injection lands in `002`).
- **Compose:** `compose.yaml` with `postgres`, `redis`, `kafka` (KRaft) + `schema-registry`, `keycloak` (realm import), `mailpit`, `prometheus`, `grafana` (provisioned datasources + an empty "Sale Overview" dashboard), `tempo`, `loki`, `inventory-service`, `psp-simulator`; health checks + `depends_on: service_healthy`; `infra` and `full` profiles; named volumes; resource limits. `.env.example` documents every variable.
- **Images:** Jib config in the `sellout.spring-service` convention plugin — distroless Java 25 pinned by digest, non-root, layered, JVM flags, `HealthCheck` main class.
- **Makefile:** `help`, `check` (compile, spotless, checkstyle, unit + property + archunit, contract, jacoco), `test-int`, `format`, `up`, `up-infra`, `down`, `logs`, `build` (Jib to local Docker), `scan` (Trivy), `e2e` (stub until `005`), `load` (stub until `006`), `clean`. `make check` == CI's PR gate.
- **CI (GitHub Actions):** `ci.yml` — compile → spotless+checkstyle → unit+property+archunit → contract → integration (Testcontainers) → Jib build → Trivy → gitleaks. (The contract stage arrives with spec 001; gitleaks runs first.) Gradle + Testcontainers caches. `nightly.yml` with stubbed mutation/load/chaos jobs. `CODEOWNERS`, PR template with the Definition-of-Done checklist, Renovate config.
- **Pre-commit:** Spotless check + unit tests via a Git hook installed by `make install-hooks`; gitleaks. (As built, gitleaks runs in CI, not in the local hook.)
- **Docs:** `README.md` quickstart, `docs/RUNBOOK.md` stub, `docs/adr/` index updated with any decision made during scaffolding.

## Out of scope
- Any hold, saga, admission, or notification logic (that's `001`+). Helm/kind (`005`), Terraform (`007`), the React UI (`008`), k6 scenarios (`006`). Grafana panels beyond the empty provisioned dashboard (`004`).

## Design / approach
- **Convention plugins over copy-paste.** Every module's `build.gradle.kts` is a handful of lines: `plugins { id("sellout.spring-service") }` plus its own dependencies.
- **Readiness is composable.** Spring Boot's `HealthIndicator` is the seam: every indicator except liveness joins the readiness group behind `/readyz`, so the Postgres, Redis, and Kafka adapters in later slices contribute theirs automatically. `/readyz` is red until Flyway completes.
- **Security config is a library**, so no service can forget to validate tokens; opting out is impossible, opting in is automatic.
- **Testcontainers reuse** (`testcontainers.reuse.enable=true` in `~/.testcontainers.properties`, documented) keeps `make test-int` under a couple of minutes locally.
- Metrics: none new (business metrics start in `001`); JVM/HTTP/logback defaults exposed.
- Config: `SELLOUT_DB_URL`, `SELLOUT_REDIS_URL`, `SELLOUT_KAFKA_BOOTSTRAP`, `SELLOUT_OIDC_ISSUER`, `SELLOUT_OIDC_AUDIENCE` and their `.env.example` defaults.

## Invariants this slice must keep
- None of SPEC §4.4/§6 are touched. The slice's own invariant: **no unauthenticated request reaches a business endpoint** (proved by the 401 matrix on the placeholder endpoint).

## Acceptance criteria
- [x] `make up` brings the full dev stack healthy (every health check green) with one command; `make up-infra` brings up backing services only. (Tempo and Loki ship shell-less images, so they have no container health check.)
- [x] `make check` runs compile, Spotless, Checkstyle, Error Prone, unit + property + ArchUnit, contract, and the JaCoCo threshold, and is green; the Git hook runs Spotless + unit. (contract tests arrive with spec 001)
- [x] `make test-int` runs the Testcontainers suites green, including the real-Keycloak resource-server test.
- [x] `curl /healthz` and `/readyz` pass on `inventory-service`; `/readyz` is red while Flyway is running or Postgres is down; `/actuator/prometheus` exposes JVM and HTTP metrics.
- [x] `GET /events/{id}` without a token → `401`; with a `CUSTOMER` token → `200` with the seeded event; with a tampered token → `401`.
- [x] ArchUnit fails the build if `domain` imports Spring or JPA, or if `application` imports `adapters`.
- [x] Boot with a missing required property **fails fast** with a clear message naming the property.
- [x] `make build` produces distroless, non-root images via Jib; the container passes its `HealthCheck`.
- [ ] Trivy reports no HIGH/CRITICAL on both images. — verified when the first PR runs (Trivy runs in CI)
- [ ] CI runs the PR pipeline green on a trivial change; Gradle and Testcontainers caches hit on re-run. — verified when the first PR runs
- [x] `README.md` quickstart works from a clean clone on a machine with Docker + JDK 25 (Windows notes for `make` included, as in the Atlas README).

## Tests
- Unit: `@ConfigurationProperties` validation (missing/invalid → error naming the property); `/healthz` and `/readyz` handlers with a toggleable stub `HealthIndicator`; JSON log layout includes trace fields; `Clock` injection.
- Architecture: ArchUnit rules for the hexagonal layout and no module cycles (a deliberately violating test module proves the rule fires, then is deleted in the same commit).
- Integration: Flyway migrates on Testcontainers Postgres and `ddl-auto=validate` passes; Keycloak issues a token that the resource server accepts; `/readyz` flips red when the Postgres container is paused.
- Security: 401 matrix on `GET /events/{id}`; tampered-signature and wrong-audience tokens rejected.
- CI meta: the workflow itself runs on this PR.
