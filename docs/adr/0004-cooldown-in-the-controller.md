# 0004 — Keep the watering cooldown in the controller

**Status:** accepted, 2026-09

## Context

After the controller commands watering, the pump takes a few seconds and the
next readings still show dry soil. Without protection, every reading in that
window triggers another command. The device could ignore repeats, the
controller could wait, or both.

## Decision

`WateringPolicy` tracks the last watering per device and refuses to water again
within a cooldown (15 s by default: pump time plus one telemetry interval, with margin). The device stays simple and does what it is
told.

## Consequences

- The control loop's behaviour is visible and testable in one place, with an
  injected `Clock`.
- A wet reading does not reset the cooldown; only time does.
- The cooldown is in-memory, which is one of the reasons the controller runs as a
  single instance (ADR 0005).
