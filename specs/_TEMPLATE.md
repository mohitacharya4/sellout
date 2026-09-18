# NNN — <Feature name>

**Status:** not started | in progress | done
**Depends on:** <spec numbers, or none>

## Goal
One or two sentences: what this slice delivers and why. Tie back to SPEC §.

## In scope
- Concrete, checkable deliverables.

## Out of scope
- What this slice deliberately does NOT do (defer to which spec).

## Design / approach
- Key modules touched (paths), data shapes, ports, events. Keep `domain` pure (Constitution §2.1).
- New config properties / env vars; new metrics (SPEC §10 names); new topics or schema changes.
- Trade-offs worth an ADR → `docs/adr/NNNN-*.md`.

## Invariants this slice must keep
- Which SPEC §4.4 / §6 invariants are touched, and which test proves each.

## Acceptance criteria (Definition of Done — Constitution §9 + Practices §6)
- [ ] Behaviour X verified by test Y
- [ ] Domain logic unit- and property-tested; contention paths have a race test
- [ ] Every new endpoint has its 401/403 matrix and IDOR test
- [ ] Metric + dashboard panel + runbook note for any new operational surface
- [ ] `make check` and `make test-int` green
- [ ] Docs / README / ADRs updated

## Tests
- Unit: …
- Property: …
- Integration / concurrency: …
- Security: …
