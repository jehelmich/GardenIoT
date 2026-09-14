# 0007 — A plant's needs travel as reported device state

**Status:** accepted, 2026-09

## Context

One humidity threshold for the whole garden waters a cactus into root rot and
lets a fern wilt. The controller needs to know what each plant wants, and the
device is the one that knows.

## Decision

The device reports a `WateringProfile` (name, minimum, maximum humidity) as
device state: a reported property on the IoT Hub device twin, a retained topic
on MQTT. The controller reads it through a `DeviceProfileSource` port — a
retained-topic cache on MQTT, a `TwinClient` lookup with a five-minute cache on
Azure — and the policy waters below *that* threshold, falling back to the
garden-wide default for a device that reported nothing.

## Consequences

- Per-device configuration uses the mechanism IoT Hub provides for it, not a
  side channel or a field smuggled into every telemetry message.
- A freshly started controller knows every plant's needs before the first
  reading arrives on MQTT (the broker replays retained messages); on Azure it
  costs one twin read per device per five minutes.
- The desired-properties direction (the cloud telling a device its threshold)
  is the natural next step and would use the same port.
