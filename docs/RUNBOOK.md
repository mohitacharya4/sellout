# Sellout — Runbook

Operational procedures. Each slice adds the entries for the surface it introduces (Practices §5).

## Local stack
- **Start / stop:** `make up` / `make down`. `make up-infra` for backing services only.
- **Reset everything (drops volumes):** `make clean`.
- **Logs:** `make logs`. Services log ECS JSON; `traceId` links to Tempo.
- **Keycloak admin:** http://localhost:8180, credentials in `.env`. Realm is imported from `ops/keycloak/realm-sellout.json` on start; edit the file and restart Keycloak, do not click in the console. `--import-realm` skips an already-existing realm, so a realm-file edit needs `docker compose up -d --force-recreate keycloak`, not a plain restart.

## Health
- `/healthz` liveness, `/readyz` readiness (Postgres and every other indicator), `/actuator/prometheus`.
- A red `/readyz` with a green `/healthz` means a dependency is down; check `docker compose ps`.
- Tempo and Loki ship on scratch images with no shell, so they have no container health check; Prometheus and Grafana still reach them, and `docker compose ps` will show them without a health column. Loki is running but nothing ships logs to it yet — no collector is configured; log shipping arrives with 004.

## Coming with later slices
- 001: force-release a hold, run the sweeper by hand. 002: replay a DLQ, reconcile orders. 003: rotate the admission signing key. 004: alert runbooks. 005: drain a consumer, roll a pod. 006: run the load test and publish results.
