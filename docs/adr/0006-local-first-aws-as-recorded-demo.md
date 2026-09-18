# 0006 — Local-first on compose and kind; AWS is a one-shot recorded demo

**Status:** accepted
**Date:** 2026-09-18
**Spec:** `specs/000-scaffold.md`, `specs/005-kubernetes.md`, `specs/007-aws.md`

## Context
The project must demonstrate AWS, Kubernetes, and Terraform depth without a standing cloud bill, and every developer loop, test, and load run must work on a laptop with no credentials. A portfolio that only works when an EKS cluster is up is a portfolio that stops working.

## Decision
Everything runs locally: Docker Compose for the dev loop, a kind cluster with Strimzi and KEDA for Kubernetes, k6 against kind for load. Terraform modules for VPC, EKS, RDS, ElastiCache, MSK, ECR, IRSA, and Secrets Manager are real, linted and `plan`-checked in CI, and applied only for a recorded demo via `make aws-up` followed by `make aws-down` in the same session. Every managed AWS dependency has a local equivalent behind the same configuration seam (Keycloak ↔ Cognito, Strimzi ↔ MSK, Postgres container ↔ RDS, Redis container ↔ ElastiCache).

## Alternatives considered
- **Always-on AWS environment** — costs money continuously, and the project would rot the moment the account is paused.
- **LocalStack for everything** — good for S3/SQS-style APIs, weak for EKS, RDS, and MSK; the interesting parts of this project are exactly the ones it does not emulate well.
- **No AWS at all** — drops a headline skill the project exists to show.

## Consequences
- Every service reads its backing-service locations from config; no code path knows whether it is on AWS.
- Terraform is a CI-validated artifact and a demo script, not the primary runtime. Published load-test numbers come from kind with the hardware spec stated.
- `make aws-down` is part of the demo checklist and the runbook; an AWS budget alarm is part of the Terraform.
