# Sellout — Feature Specs (spec-driven development)

This folder holds one spec per feature slice. **Claude Code (and any contributor) MUST implement against a spec here — never freehand.** The flow:

1. Read `../ENGINEERING_CONSTITUTION.md` (the non-negotiables) and `../ENGINEERING_PRACTICES.md` (testing/Docker/CI/ops standards).
2. Read `../PROBLEM.md` and `../SPEC.md` (the what + the architecture).
3. Pick the lowest-numbered unfinished spec below, write a plan for it, implement exactly it with TDD (red commit first), pass all gates (`make check`, `make test-int`), open a PR.

Copy `_TEMPLATE.md` to start a new spec. Number specs in build order. Record any non-obvious trade-off as an ADR in `../docs/adr/`.

> **Headline criteria every slice protects** (SPEC §10, README): `oversell_total == 0` under the k6 spike · hold p99 < 150 ms at target RPS on kind · every expired hold released within TTL + 5 s · every saga reaches a terminal state · PSP faults never cause a double charge or a lost seat.

## Build order (thin vertical slice first — see SPEC §13)

| # | Spec | Status |
|---|---|---|
| 000 | [Project scaffold](000-scaffold.md) — Gradle Kotlin DSL multi-module + convention plugins, `libs/` skeleton, compose infra (Postgres, Redis, Kafka, Keycloak, Mailpit, observability stack), Keycloak realm + resource-server baseline, `/healthz` `/readyz` `/actuator/prometheus`, ArchUnit, CI gates, Jib images, Makefile. **Do this first.** See `../ENGINEERING_PRACTICES.md` | 🔲 not started |
| 001 | Inventory — seat model, hold/release/confirm, Redis Lua pre-check + Postgres conditional update, fencing tokens, TTL sweeper, per-user limits, ownership authz; **concurrency + property proofs of no-oversell**; outbox events | 🔲 |
| 002 | Order saga — PSP simulator with fault injection, saga state machine, inbound/outbound idempotency, webhook HMAC + replay window, deadline sweeper, refund compensation, consumer dedupe, DLQ | 🔲 |
| 003 | Admission — Redis waiting room, release scheduler tied to inventory capacity, signed admission tokens bound to `(sub, eventId)`, SSE position stream, load shedding; edge-gateway with JWT validation, per-user rate limit, admission-token check | 🔲 |
| 004 | Observability — end-to-end traces, business metrics on every service, Grafana dashboards + SLO burn-rate alerts as code, reconciliation job | 🔲 |
| 005 | Kubernetes — Helm charts, kind, Strimzi Kafka, KEDA/HPA, probes, PDBs, NetworkPolicies, Kafka ACLs, External Secrets, Trivy + dependency-check gates | 🔲 |
| 006 | Load + chaos — k6 spike (100k VUs / 5k seats), Toxiproxy scenarios; results and dashboard screenshots committed to `docs/results/` and the README | 🔲 |
| 007 | AWS — Terraform modules (VPC, EKS, RDS, ElastiCache, MSK, ECR, IRSA, Secrets Manager), `validate`/`tflint`/`plan` in CI, `make aws-up` / `make aws-down` one-shot demo | 🔲 |
| 008 | React demo UI — PKCE login, waiting room with live position, seat map, checkout, ticket view | 🔲 |
| 009 | Notification service — idempotent `ticket.issued` consumer, email via Mailpit | 🔲 |
| 010 | Stretch — CQRS read model for the seat map, Linkerd mTLS, multi-region notes, remaining ADRs | 🔲 |

Specs `001`–`010` are written when their turn comes, after the previous slice ships. Only `000` exists at the spec stage so the scaffold is not designed around features that may change.
