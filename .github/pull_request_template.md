## What & why

<!-- One paragraph. Which spec does this implement? Link it. -->

Implements: `specs/NNN-….md`

## Definition of Done (Constitution §9 + Practices §6)

- [ ] Matches its `specs/` entry — no scope creep, no speculative abstraction
- [ ] `make check` and `make test-int` green
- [ ] Domain/application logic unit- and property-tested; contention paths have a race test
- [ ] Every new endpoint has its 401/403 matrix and IDOR test
- [ ] Metric + dashboard panel + runbook note for any new operational surface
- [ ] Config typed and validated; new deps in the catalogue
- [ ] Docs / README / ADRs updated; no `TODO` in shipped paths

## Invariants touched

<!-- Which SPEC §4.4 / §6 invariants does this change touch, and which test proves each? -->

## Notes for the reviewer

<!-- Trade-offs, anything deliberately deferred, anything you want argued with. -->
