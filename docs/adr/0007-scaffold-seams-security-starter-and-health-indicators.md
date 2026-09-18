# 0007 — Security config lives in its own starter; readiness rides Spring Boot's `HealthIndicator`

**Status:** accepted
**Date:** 2026-09-18
**Spec:** `specs/000-scaffold.md`

## Context
Slice 000 has to give every later service a zero-trust baseline and a composable readiness signal without hand-wiring either one per module. Two scaffolding decisions shape everything that lands on top: where the JWT resource-server configuration lives, and what seam a service uses to say "I am not ready yet." Both had to be settled before `inventory-service` or `psp-simulator` could exist, and outbox/idempotency needed a decision on when they show up at all.

## Decision
1. Security configuration (OIDC bridge, resource-server filter chain, method security) is its own library, `libs/security-starter`, independent of `libs/observability-starter` at main scope — not folded into observability or copy-pasted per service. Every `sellout.spring-service` module depends on it, so opting out of authentication is impossible and opting in is automatic.
2. Readiness uses Spring Boot's own `HealthIndicator` contract as the extension seam instead of a custom `ReadinessIndicator` interface: `/readyz` aggregates every registered `HealthIndicator` except liveness, so a Postgres, Redis, or Kafka adapter added in a later slice contributes to readiness just by registering a bean, with no scaffold-specific API to learn.
3. `libs/outbox-starter` and `libs/idempotency-starter` are deferred to specs 001 and 002, the slices that first need them, rather than scaffolded now as empty skeletons with unused Flyway migrations and ports nothing calls yet.

## Alternatives considered
- **Security config inside `observability-starter`** — the two concerns (auth, telemetry) have no shared lifecycle; a service that only wants metrics would still pull in the OAuth2 resource-server dependency, and `libs/security-starter` must stay independent of `libs/observability-starter` at main scope regardless.
- **Security config copy-pasted per service's `build.gradle.kts` / `SecurityConfig`** — the Constitution §4 zero-trust invariant becomes something every new service must remember to add, rather than something it cannot avoid.
- **Custom `ReadinessIndicator` interface** — one more thing to implement and register per dependency, duplicating what `HealthIndicator` (with a `Readiness`/`Liveness` group) already gives us; it would also fight Boot's own actuator health aggregation instead of using it.
- **Scaffold `outbox-starter`/`idempotency-starter` now as empty skeletons** — module skeletons with no callers and a Flyway migration nothing runs are dead weight the ArchUnit rules would have to special-case, for a "done" checkbox with no behaviour behind it.

## Consequences
- Every `sellout.spring-service` module is a resource server by construction; there is no code path that serves a business endpoint without validating a bearer token (Constitution §4).
- A later slice adding a new dependency (Redis, Kafka) gets readiness for free by registering a `HealthIndicator`; no scaffold code changes.
- `libs/outbox-starter` and `libs/idempotency-starter` do not exist yet; 001 and 002 create them with their first real callers, not as pre-built skeletons.
- Proved by: `ResourceServerTest` and `MissingIssuerFailsFastTest` in `libs/security-starter`; `HealthEndpointsTest` in `libs/observability-starter` (toggleable stub `HealthIndicator` flips `/readyz`); the independence of `security-starter` from `observability-starter` is a main-scope dependency-graph fact, not a test.
