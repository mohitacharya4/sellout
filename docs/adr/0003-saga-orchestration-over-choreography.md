# 0003 — Checkout is an orchestrated saga owned by order-service

**Status:** accepted
**Date:** 2026-09-18
**Spec:** `specs/002-order-saga.md`

## Context
Checkout spans inventory (hold → confirm or release), the payment provider (authorize → capture, with webhooks), and ticket issuance. There is no distributed transaction. Something must decide what happens when the PSP times out, when a webhook arrives after the hold expired, or when a confirm fails on the unique constraint — and it must be possible to see, for any order, exactly where it is and why.

## Decision
The order service is an explicit saga orchestrator. It owns the `Order` state machine (`CREATED → PAYMENT_PENDING → CONFIRMED | CANCELLED | FAILED`), drives every step, owns every deadline, and issues every compensation (release hold, refund). Other services react to its events; they never decide the order's fate.

## Alternatives considered
- **Choreography (each service reacts to events, no coordinator)** — fewer moving parts for the happy path, but the failure logic is smeared across services, timeouts have no owner, and "where is order X stuck?" has no single answer. Wrong trade for a flow whose whole difficulty is the failure cases.
- **A workflow engine (Temporal, Camunda)** — solves the same problem well, but hides the state machine, idempotency, and outbox behind a framework, which is the depth this project exists to show. Reasonable for a real product; noted as such.
- **Two-phase commit across services** — not available across HTTP + a third-party PSP, and the blocking semantics are wrong for a spike.

## Consequences
- A `saga_stuck` metric and a deadline sweeper are required; an order past deadline is an alert, not a mystery.
- Every transition writes exactly one outbox row in its own transaction (ADR 0004).
- Compensations are first-class steps with their own tests, including refund-after-late-capture.
- The saga is property-tested to terminate under any interleaving of PSP responses and webhooks.
