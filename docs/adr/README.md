# Architecture Decision Records

One file per decision, numbered in the order they were made, never edited after acceptance except to change **Status** (superseded by, deprecated). Copy `0000-template.md` to add one. A decision belongs here when a future reader would otherwise ask "why on earth did they do it this way?" — anything a senior reviewer would push back on without the context.

| # | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | accepted |
| [0002](0002-postgres-source-of-truth-redis-precheck.md) | Postgres is the source of truth for seats; Redis is a non-authoritative pre-check | accepted |
| [0003](0003-saga-orchestration-over-choreography.md) | Checkout is an orchestrated saga owned by order-service | accepted |
| [0004](0004-transactional-outbox-for-every-event.md) | Every cross-service event goes through a transactional outbox | accepted |
| [0005](0005-admission-token-as-capability.md) | Admission tokens are signed, short-lived capabilities bound to user and event | accepted |
| [0006](0006-local-first-aws-as-recorded-demo.md) | Local-first on compose and kind; AWS is a one-shot recorded demo | accepted |
| [0007](0007-scaffold-seams-security-starter-and-health-indicators.md) | Security config is its own `security-starter` library; readiness rides Spring Boot's `HealthIndicator` | accepted |

Decisions still to be recorded as their slices land: when to move the outbox relay from polling to Debezium (`002`), the release-rate control law for admission (`003`), KEDA scaler choice (`005`), the Keycloak ↔ Cognito seam (`007`).
