# 0005 — Admission tokens are signed, short-lived capabilities bound to user and event

**Status:** accepted
**Date:** 2026-09-18
**Spec:** `specs/003-admission.md`

## Context
The waiting room only protects the backend if the *only* way to place a hold is to have been admitted. A queue position that is just a database row or a session flag can be shared, sold, or replayed by a bot farm, and every check against it is a round trip to the admission service under peak load.

## Decision
When admission releases a user it mints a compact signed JWT `{sub, eventId, jti, exp = now + 5 min}` with a key only the admission service holds. The gateway and the inventory service verify the signature offline and check that `sub` matches the caller's identity token and `eventId` matches the request. A used `jti` is recorded in Redis for the token's lifetime so it is single-use. No admission state is consulted on the hold path.

## Alternatives considered
- **Opaque token looked up in Redis on every hold** — a round trip on the hottest path and a shared dependency between inventory and admission at peak.
- **Session flag ("admitted = true")** — replayable and shareable; nothing binds it to the event or bounds it in time.
- **Reuse the identity JWT with a custom claim** — would require the admission service to mint identity tokens or call Keycloak per admission; couples two concerns and slows the release loop.
- **Signed cookie set by the gateway** — works for browsers only; the API must also serve the load test and any non-browser client.

## Consequences
- Fairness becomes enforceable: a position cannot be transferred, and one admission yields at most one hold attempt window.
- Inventory validates the token itself (zero trust, Constitution §4.4); the gateway check is defence in depth.
- Key rotation is an operational procedure in the runbook; tokens carry a `kid`.
- Proved by: cross-user, expired, replayed, and tampered-token tests, and the fairness-order test.
