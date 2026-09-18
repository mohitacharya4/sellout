# 0004 — Every cross-service event goes through a transactional outbox

**Status:** accepted
**Date:** 2026-09-18
**Spec:** `specs/001-inventory.md`, `specs/002-order-saga.md`

## Context
A service commits a state change and then publishes an event to Kafka. If the process dies between the two, the world diverges: a hold exists that nobody was told about, or an order is confirmed and inventory never sells the seat. The reverse order (publish then commit) fails the other way. This is the dual-write problem and it is the most common source of "stuck" states in event-driven systems.

## Decision
Every service that publishes owns an `outbox` table. A state change and its event are inserted in the same database transaction. A relay reads unpublished rows in order, publishes to Kafka keyed by aggregate id, and marks them published. The relay is at-least-once; every consumer dedupes on the event id, with the dedupe record written in the same transaction as the consumer's own effect. Direct publishing from a request path is forbidden (Constitution §3.5).

## Alternatives considered
- **Publish after commit, best effort** — loses events on crash; the failure is silent and shows up as a support ticket.
- **Kafka transactions across DB + broker** — Kafka transactions cover Kafka, not Postgres; there is no atomic commit across both.
- **Event sourcing (the event log *is* the state)** — coherent, but changes the persistence model of every service and is a larger bet than this project needs. Noted as a stretch design.
- **Change data capture from the start (Debezium)** — the same outbox pattern with a better relay. Chosen as the documented upgrade path; the first implementation is a polling relay so the mechanism is visible and testable without Kafka Connect.

## Consequences
- `libs/outbox-starter` provides the table, the relay, and the publisher once; services never hand-roll it.
- `outbox_lag_seconds` is a first-class metric and has an alert.
- Per-aggregate ordering is preserved by partition key; nothing may assume global ordering.
- Proved by: the relay-crash test (kill after publish, before mark), the duplicate-delivery consumer tests.
