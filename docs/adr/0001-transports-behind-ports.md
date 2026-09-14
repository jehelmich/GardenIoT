# 0001 — Put the transports behind ports and add an MQTT profile

**Status:** accepted, 2026-09

## Context

The 2017 code called the Azure IoT SDKs directly from the application classes.
The hub it was written against no longer exists, and nobody can try the project
without creating one. The interesting part — the control loop, the simulation,
the failure handling — has nothing to do with which broker carries the messages.

## Decision

Define three small ports in `common` (`DeviceTransport`, `TelemetrySource`,
`DeviceCommandSender`, plus the `CommandHandler`/`CommandResult` pair modelled
on direct methods) and implement them twice: `transport-azure` for IoT Hub and
`transport-mqtt` for any MQTT 5 broker. `TRANSPORT` selects one at start-up.
The MQTT profile is the default because it runs anywhere.

## Consequences

- The system runs on a laptop with `docker compose up`; the Azure profile is a
  values switch rather than a prerequisite.
- Unit tests need no SDK; the integration tests run the real loop against
  Mosquitto in Testcontainers.
- Two adapters to keep in step. The `CommandResult` shape (status + JSON) keeps
  the device code identical on both.
- Fleet-level commands ("add a plant") exist only on MQTT, because IoT Hub
  devices have to be registered first. The port makes that explicit
  (`fleetChannel()` returns empty) instead of hiding it.
