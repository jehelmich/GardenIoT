# GardenIoT

Automatic plant watering on [Azure IoT Hub](https://learn.microsoft.com/azure/iot-hub/):
a garden device reports soil humidity, a cloud-side controller watches the
telemetry stream and tells the device to run its pump when the soil gets dry.

The device here is simulated — a small model of a pot drying out in the sun —
so the whole loop runs on a laptop against a free-tier hub. Everything that
talks to the hub is real: device-to-cloud messages, direct methods, and
reported properties on the device twin.

A hobby project from July 2017, brought up to date in 2026: current Azure
SDKs, Java 21, a multi-module Maven build with tests and CI. See
[Status](#status).

## How it works

```
 ┌──────────────────────┐   telemetry (MQTT)    ┌──────────────┐   Event Hub-compatible   ┌──────────────┐
 │   simulated-device   │ ────────────────────▶ │              │ ───────────────────────▶ │  controller  │
 │                      │                       │  Azure       │        endpoint          │              │
 │  PlantSimulation     │   direct method       │  IoT Hub     │   direct method          │ Watering-    │
 │  DirectMethodHandler │ ◀──────────────────── │              │ ◀─────────────────────── │ Policy       │
 │                      │   "water"             │              │   "water"                │              │
 └──────────┬───────────┘                       └──────────────┘                          └──────────────┘
            │  reported property: lastWater = <timestamp>
            └───────────────────────────────────────▶  device twin
```

1. Every five seconds the **device** takes a reading from `PlantSimulation`
   (temperature does a small random walk; humidity evaporates a little on
   each step) and publishes it as a JSON message:

   ```json
   {"deviceId":"garden-1","timestamp":"2017-07-17T10:15:30Z","temperature":22.4,"humidity":31.9}
   ```

2. The **controller** reads every partition of the hub's built-in Event
   Hub-compatible endpoint, starting from now, and hands each message to a
   `WateringPolicy`. When humidity drops below the threshold (25 % by default)
   the policy says water — but not more than once per cooldown, because the
   pump takes a while and the next few readings still show dry soil.

3. The controller invokes the `water` **direct method** on that device. The
   device acknowledges with `202 Accepted`, runs the pump on a background
   thread, soaks the simulated soil to 100 %, and records `lastWater` as a
   **reported property** on its twin. A `reboot` method works the same way.

The message format is the one contract between the two halves, so it lives
in a shared `common` module as a Java record with its Gson codec.

## Repository layout

```
common/            Telemetry record + JSON codec; typed access to environment variables
simulated-device/  DeviceApp — telemetry loop, direct method handler, plant model
controller/        ControllerApp — Event Hub consumer, watering policy, direct method invoker
.github/           CI workflow and Dependabot configuration
```

Each application module builds a runnable fat jar.

## Running it

You need a JDK 21 or newer and an Azure IoT Hub with one registered device
(the free F1 tier is enough). Maven is fetched by the wrapper.

```sh
./mvnw verify          # compile, run the tests, package both jars
```

Configuration is passed through the environment; nothing secret is ever
written to a file in this repository.

| Variable | Used by | Meaning |
|---|---|---|
| `IOTHUB_DEVICE_CONNECTION_STRING` | device | The device's connection string (`HostName=…;DeviceId=…;SharedAccessKey=…`). The device id is taken from it. |
| `TELEMETRY_INTERVAL_SECONDS` | device | Seconds between readings. Default `5`. |
| `ACTION_DURATION_SECONDS` | device | How long the simulated pump / reboot takes. Default `5`. |
| `EVENTHUB_COMPATIBLE_CONNECTION_STRING` | controller | Connection string of the hub's built-in endpoint (*Hub-level settings → Built-in endpoints*), including `EntityPath`. |
| `EVENTHUB_CONSUMER_GROUP` | controller | Consumer group to read from. Default `$Default`. |
| `IOTHUB_SERVICE_CONNECTION_STRING` | controller | A shared access policy with *service connect* permission, for invoking direct methods. |
| `HUMIDITY_THRESHOLD` | controller | Water below this soil humidity, in percent. Default `25`. |
| `WATERING_COOLDOWN_SECONDS` | controller | Minimum time between two watering commands to the same device. Default `60`. |

Start the controller first so it sees the device's messages from the start,
then the device, each in its own terminal:

```sh
export EVENTHUB_COMPATIBLE_CONNECTION_STRING='Endpoint=sb://…'
export IOTHUB_SERVICE_CONNECTION_STRING='HostName=…;SharedAccessKeyName=service;SharedAccessKey=…'
java -jar controller/target/controller-1.0.0-SNAPSHOT.jar
```

```sh
export IOTHUB_DEVICE_CONNECTION_STRING='HostName=…;DeviceId=garden-1;SharedAccessKey=…'
java -jar simulated-device/target/simulated-device-1.0.0-SNAPSHOT.jar
```

With the defaults the soil starts at 26 % and crosses the threshold within a
few readings, so the first watering happens in about half a minute:

```
10:15:30.412 INFO ControllerApp - Watching 'garden-hub' on consumer group '$Default'; watering below 25.0% humidity. Press Ctrl-C to stop.
10:15:41.007 INFO TelemetryProcessor - garden-1: temperature=22.1°C humidity=25.8%
10:15:46.012 INFO TelemetryProcessor - garden-1: temperature=22.0°C humidity=25.5%
10:15:51.010 INFO TelemetryProcessor - garden-1: temperature=22.1°C humidity=25.3%
10:15:56.014 INFO TelemetryProcessor - garden-1: temperature=22.2°C humidity=25.1%
10:16:01.011 INFO TelemetryProcessor - garden-1: temperature=22.2°C humidity=24.9%
10:16:01.011 INFO TelemetryProcessor - garden-1: soil is dry, requesting watering
10:16:01.013 INFO DirectMethodWateringActuator - Invoking 'water' on device 'garden-1'
10:16:01.388 INFO DirectMethodWateringActuator - Device 'garden-1' answered 202 {"message":"Started watering"}
10:16:06.015 INFO TelemetryProcessor - garden-1: temperature=22.3°C humidity=24.6%
10:16:11.012 INFO TelemetryProcessor - garden-1: temperature=22.3°C humidity=99.8%
```

Both programs run until interrupted with Ctrl-C.

## Design notes

- **One wire contract.** `Telemetry` is a record in `common`; the device
  serialises it and the controller parses it with the same codec, which
  rejects malformed and out-of-range documents instead of passing them on.
- **Testable seams.** The classes that do the work — `PlantSimulation`,
  `DirectMethodHandler`, `TelemetryPublisher`, `WateringPolicy`,
  `TelemetryProcessor` — depend on small interfaces (`TelemetrySink`,
  `PropertyReporter`, `WateringActuator`) and an injected `Clock`, so the
  unit tests run without a hub and without sleeping.
- **Defensive by default.** A message that cannot be parsed, a twin update
  that fails, or a device that rejects a command is logged and skipped; it
  never takes down the loop. Direct methods are acknowledged immediately and
  executed off the callback thread.
- **Clean lifecycle.** Both entry points register a shutdown hook, close their
  clients on SIGINT/SIGTERM, and the controller exits non-zero if the receive
  link fails for good rather than idling on the SDK's reactor threads.
- **No secrets in git.** Configuration comes from the environment and is
  validated up front with a one-line error, not a stack trace.

## Status

The 2017 version was developed against a real IoT Hub; the intended follow-up,
replacing the simulation with physical sensors, never happened. The 2026
revision was verified as far as it can be without a hub: the build and unit
tests pass, both jars start, read their configuration, and fail cleanly
against unreachable or unauthorised endpoints. The direct-method round trip
has not been re-run end to end against a live hub since the SDK migration.

## License

[Apache License 2.0](LICENSE).
