# Sellout — Flash-Sale Ticketing Platform

> **Sell out in seconds. Oversell never.**

100,000 buyers press *Buy* for 5,000 seats in the same second. Sellout admits them **fairly** through a virtual waiting room, places seat **holds that can never overlap**, runs checkout as a **saga** that survives a flaky payment provider without double charges or lost seats, autoscales on business signals — and the Grafana dashboard proves it.

A production-grade Java portfolio project: Java 25 (virtual threads), Spring Boot 4, Gradle Kotlin DSL monorepo, Postgres, Redis, Kafka, Keycloak, OpenTelemetry, Prometheus/Grafana, Docker, Kubernetes (kind + Helm + KEDA), Terraform for AWS, and TDD throughout with Testcontainers.

**Status:** spec stage. The product brief, architecture spec, engineering standards, and the first slice spec are written; no code yet. See [`specs/README.md`](specs/README.md) for the build order.

---

## Headline criteria (to be measured, not asserted)

These go green, with committed results in `docs/results/`, before the project is called done:

| Criterion | How it is proved |
|---|---|
| **0 oversells** across a k6 spike of 100k virtual users on 5k seats | `oversell_total` metric + reconciliation job after every load test |
| Seat-hold **p99 < 150 ms** at target RPS on a local kind cluster | k6 thresholds; number published with the hardware spec |
| Every expired hold released within **TTL + 5 s**; every saga reaches a terminal state | `holds_expired_total`, `saga_stuck == 0` |
| PSP faults (timeouts, 5xx, duplicate and late webhooks) **never** cause a double charge or a lost seat | Toxiproxy chaos runs + idempotency tests |

## Architecture at a glance

```
React UI ──JWT──► edge-gateway ──► admission-service ──(admission token)──► inventory-service ──► Postgres
                  rate limit         Redis waiting room                        Redis pre-check + conditional update
                  token check        SSE positions                             fencing tokens, TTL sweeper
                                                                                      │ outbox → Kafka
                                     order-service  ◄────────────────────────────────┘
                                     saga: hold → pay → confirm → issue
                                     idempotency keys, outbox, deadlines, DLQ
                                          │ authorize / capture        webhooks (HMAC)
                                          ▼                                  │
                                     psp-simulator (latency, 5xx, duplicate/late webhooks)
                                     notification-service ◄── ticket.issued ── Mailpit
```

Full detail in [`SPEC.md`](SPEC.md).

## Documentation

| Document | What it covers |
|---|---|
| [`PROBLEM.md`](PROBLEM.md) | The domain — what "good" means for a flash-sale ticketing system |
| [`SPEC.md`](SPEC.md) | Architecture: contention model, waiting room, saga, outbox, security, observability, platform, build order |
| [`ENGINEERING_CONSTITUTION.md`](ENGINEERING_CONSTITUTION.md) | The non-negotiables. Read before writing code. |
| [`ENGINEERING_PRACTICES.md`](ENGINEERING_PRACTICES.md) | Testing, Docker, CI/CD, ops standards |
| [`specs/`](specs/README.md) | One spec per slice, in build order |
| [`docs/adr/`](docs/adr/README.md) | Architecture decision records — why each major trade-off went the way it did |

## Contributing

1. Read the Constitution and Practices.
2. Pick the lowest-numbered unfinished spec in [`specs/README.md`](specs/README.md).
3. Implement exactly that spec, test-first — no scope creep, no speculative abstraction.
4. `make check` and `make test-int` green, then open a PR. A red gate blocks merge; there are no manual overrides.

## License

MIT — see [LICENSE](LICENSE).
