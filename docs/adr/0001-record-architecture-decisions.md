# 0001 — Record architecture decisions

**Status:** accepted
**Date:** 2026-09-18
**Spec:** all

## Context
Sellout exists to demonstrate judgement in distributed-systems trade-offs. The code shows *what* was chosen; without a record, the *why* lives only in the author's head and cannot be reviewed, defended in an interview, or revisited when the constraints change.

## Decision
Keep lightweight ADRs in `docs/adr/`, one per decision, numbered, immutable after acceptance except for status changes. Every spec's "Design / approach" section lists the ADRs it produces.

## Alternatives considered
- **Comments in code** — scattered, rot with refactors, invisible to a reader skimming the repo.
- **A single DECISIONS.md** — becomes a wall; no per-decision status or supersession.

## Consequences
- Reviewers and interviewers can trace every non-obvious choice to its reasoning.
- Writing one is part of the Definition of Done for any non-obvious trade-off (Practices §6).
