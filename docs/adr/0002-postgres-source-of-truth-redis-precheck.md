# 0002 — Postgres is the source of truth for seats; Redis is a non-authoritative pre-check

**Status:** accepted
**Date:** 2026-09-18
**Spec:** `specs/001-inventory.md`

## Context
Thousands of requests target the same few seats in the same second. A pure-Postgres design serialises every losing request on row locks and the database becomes the bottleneck. A pure-Redis design is fast but Redis is not durable by default, cluster failover can lose acknowledged writes, and a lock with a TTL is not a guarantee (a paused client can wake up after its lock expired and still act). The system must be *fast* on the hot path and *correct* regardless of what Redis does.

## Decision
A hold is granted only when a Postgres conditional update (`UPDATE seat … WHERE status = 'AVAILABLE'`) affects exactly the requested rows, in the same transaction as the hold row and the outbox event. A Redis Lua script runs first as an atomic pre-check that rejects most losing requests without touching Postgres; it may reject, it may never grant. Every hold carries a fencing token from a Postgres sequence, and confirm/release commands are rejected if the token is stale. A unique constraint on `ticket(event_id, seat_id)` is the last line of defence and increments `oversell_total` if it ever fires.

## Alternatives considered
- **Postgres only, `SELECT … FOR UPDATE`** — correct but the hot rows serialise the spike; p99 collapses under load.
- **Redis only (Redlock or single-key locks)** — fast but not authoritative; the pause-after-expiry problem and failover semantics make it unsafe as the truth.
- **Optimistic locking with a `version` column alone** — correct, but every loser pays a full round trip and a retry; the pre-check gives the same correctness with far fewer database hits.
- **Application-level in-memory lock per event with sticky routing** — a single point of failure and does not survive a pod restart.

## Consequences
- Every hold path must be correct with Redis flushed; that is a test, not a hope.
- Redis key TTLs are set longer than the Postgres TTL so the sweeper (Postgres) always decides first.
- Fencing tokens are mandatory on every mutating hold command (Constitution §3.2).
- Proved by: the 200-thread race test, the late-confirm-after-expiry test, the jqwik invariants, and `oversell_total == 0` during the k6 spike.
