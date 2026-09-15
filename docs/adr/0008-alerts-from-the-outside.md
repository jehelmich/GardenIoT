# 0008 — Detect device faults from the outside, and say so on the twin

**Status:** accepted, 2026-09

## Context

A cheap sensor does not know when it is lying. Once things could break on
their own (sensor stuck, drifting or silent, pump dead), something had to
notice, and the device itself can only notice the pump: its flow meter sees
nothing come out. Everything about the sensor has to be inferred from what
arrives in the cloud.

## Decision

The controller runs an `AnomalyDetector` over the telemetry it already
receives: a humidity value repeated to the digit for eight readings is a
stuck sensor (except at the sensor's saturation limits, where constant
readings are real); soil that does not get wetter after an accepted watering
is an ineffective watering; no reading for a minute is silence; a `5xx`
answer to `water` is a pump fault the device reported itself. Each alert is
raised once, cleared when the symptom passes, exposed as a metric, and
published through an `AlertPublisher` port — a retained topic on MQTT, the
`alerts` desired property of the device twin on IoT Hub — so the device's own
page can show "controller says" next to "device says".

Drift is deliberately not detected: a slowly rising offset is indistinguishable
from a slowly changing garden without a second sensor or a model. The game
uses it as the case where the human has to look.

## Consequences

- Faults become visible in the place where a real operations team would look
  for them, without any new channel: the twin already exists on both transports.
- The detector is pure and clock-injected, so its rules are unit tested; the
  false positives found in a live run (saturated soil at 100 %, dry soil at 3 %)
  turned into tests.
- Alerts are advisory. The controller keeps watering by the rules; the
  human sends the technician.
