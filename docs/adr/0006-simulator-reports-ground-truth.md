# 0006 — Let the simulator report its ground truth

**Status:** accepted, 2026-09

## Context

A sensor that lies is the most interesting failure in a sensing-and-actuating
loop: the controller does exactly the right thing with the wrong information and
the plant dies anyway. In a simulation that is easy to show — if the observer can
see what is really happening.

## Decision

Next to the sensor reading, each virtual device reports `SimulationState` —
true humidity, fault, speed, waterings — as retained state and as Prometheus
gauges. The dashboard draws reported and true humidity on the same axis.

## Consequences

- Sensor faults (`STUCK`, `OVERREAD`, `SILENT`) become a visible story rather
  than a silent divergence.
- The extra state topic and gauges are clearly labelled as simulator-only; a
  real device could not produce them.
