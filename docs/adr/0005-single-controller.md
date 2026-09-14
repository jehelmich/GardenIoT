# 0005 — Run one controller

**Status:** accepted, 2026-09

## Context

Every controller instance receives every reading. During a rolling update on
kind, two controllers briefly ran side by side and each plant received the
`water` command twice.

## Decision

The controller runs as exactly one replica: `replicas: 1` in the Helm chart,
`max_replicas = 1` on Container Apps, both with a comment saying why.

## Consequences

- No duplicate commands; the cooldown map (ADR 0004) is consistent.
- A restart is a gap of a few seconds during which nothing waters. For a garden
  that is fine.
- Scaling out would need partitioned consumption (the processor client of
  ADR 0003 with one consumer group) or leader election — a deliberate
  non-goal for this project.
