# GardenIoT

[![CI](https://github.com/jehelmich/GardenIoT/actions/workflows/ci.yml/badge.svg)](https://github.com/jehelmich/GardenIoT/actions/workflows/ci.yml)
[![CodeQL](https://github.com/jehelmich/GardenIoT/actions/workflows/codeql.yml/badge.svg)](https://github.com/jehelmich/GardenIoT/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Automatic plant watering as a small, complete IoT system: simulated garden devices
report soil humidity, a cloud-side controller watches the stream and commands the
pump when a plant gets dry. It runs on a laptop with one command, on Kubernetes
with a Helm chart, or on Azure IoT Hub with Terraform — same code, different
transport.

```sh
docker compose up --build        # then open http://localhost:3000
```

![Grafana dashboard: reported vs. true soil humidity, waterings, sensor faults](docs/images/grafana.png)

*Two plants at 25× simulation speed. Basil dries out and gets watered in a
sawtooth. Mint's humidity sensor was frozen with the `fault` command halfway
through: the controller keeps seeing 96 % (solid line) while the soil is
actually bone dry (dashed) — and, trusting its sensor, never waters it again.*

A hobby project from July 2017, rebuilt in 2026 as a portfolio piece. See
[Status](#status) for what has and has not been verified.

## How it works

```
                 telemetry                          telemetry
   ┌────────────┐ ───────▶ ┌─────────────────────┐ ───────▶ ┌────────────┐
   │  device(s) │          │  transport           │          │ controller │
   │            │ ◀─────── │  MQTT broker  - or - │ ◀─────── │            │
   │ Plant-     │ commands │  Azure IoT Hub       │ commands │ Watering-  │
   │ Simulation │          └─────────────────────┘          │ Policy     │
   └─────┬──────┘                                            └─────┬──────┘
         │ /metrics                                                │ /metrics
         └──────────────▶  Prometheus  ──▶  Grafana  ◀─────────────┘
```

1. Every few seconds each **device** advances its `PlantSimulation` (temperature
   wanders, soil dries a little faster when warm), reads its sensor and publishes
   a JSON reading. The sensor can be broken on command — stuck, over-reading or
   silent — while the plant keeps drying underneath.
2. The **controller** subscribes to every device's telemetry and hands each
   reading to a `WateringPolicy`: below the threshold, water — but not more than
   once per cooldown, because the next few readings still show dry soil while
   the pump runs.
3. The controller sends the `water` **command**. The device acknowledges with
   `202 Accepted`, runs the pump on a background thread, soaks the soil, and
   reports `lastWater` as device state. Direct method on IoT Hub, request/response
   over MQTT 5 — the application code does not know which.
4. Both processes expose Prometheus metrics. The device also exposes the
   simulator's **ground truth** next to what its sensor claimed, which is how the
   dashboard shows a lying sensor.

The applications talk to *ports* (`DeviceTransport`, `TelemetrySource`,
`DeviceCommandSender`); `transport-mqtt` and `transport-azure` are the adapters.
`TRANSPORT=mqtt|azure` picks one at start-up. Details and the reasons behind the
design are in [docs/architecture.md](docs/architecture.md) and the
[decision records](docs/adr/).

## Repository layout

```
common/            Telemetry contract, transport ports, configuration and metrics helpers
transport-mqtt/    MQTT 5 adapter (HiveMQ client): topics, request/response, last will, fleet channel
transport-azure/   Azure IoT Hub adapter: Entra ID or connection strings, direct methods, twins
simulated-device/  A fleet of virtual plants with sensor faults and runtime speed control
controller/        The watering loop
integration-tests/ The loop end to end against Mosquitto in Testcontainers
deploy/helm/       Helm chart; also the source of the broker, Prometheus and Grafana config
deploy/terraform/  Azure: IoT Hub, managed identity, Container Apps
scripts/           kind-up.sh, kind-down.sh, deploy-azure.sh
docs/              Architecture, decision records, images
```

## Running it

### On a laptop (Docker Compose)

```sh
docker compose up --build
```

Brings up Mosquitto, the controller, a device process hosting `basil` and
`mint`, Prometheus and Grafana. Grafana is at <http://localhost:3000> (no login,
dashboard provisioned), Prometheus at <http://localhost:9090>. Watch the loop in
the logs, or poke at the devices directly — commands are plain MQTT 5 requests:

```sh
# 25x speed, then break mint's sensor
docker compose exec mosquitto mosquitto_pub -t garden/basil/cmd/setSpeed -m '{"factor":25}'
docker compose exec mosquitto mosquitto_pub -t garden/mint/cmd/fault    -m '{"type":"STUCK"}'

# a third device process with its own plant
docker compose run -d -e DEVICE_IDS=thyme device
```

### On Kubernetes (kind + Helm)

```sh
scripts/kind-up.sh      # builds images, creates a kind cluster, installs deploy/helm/gardeniot
scripts/kind-down.sh
```

The chart runs devices as a StatefulSet (pod *N* hosts `device.plants[N]`),
the controller as a single-replica Deployment, and optionally Prometheus and
Grafana. Pods run as non-root with a read-only root filesystem; liveness and
readiness probes use `/healthz` and `/readyz`. Every value, including the Azure
profile, is documented in [`values.yaml`](deploy/helm/gardeniot/values.yaml).

### On Azure (Terraform)

```sh
az login
scripts/deploy-azure.sh           # IoT Hub (free tier), managed identity, two Container Apps
scripts/deploy-azure.sh destroy
```

The controller authenticates to the hub with a managed identity and Entra ID
roles — there is no key anywhere in its configuration. The device, like a real
one, uses its own device credential. See
[deploy/terraform/azure/README.md](deploy/terraform/azure/README.md).

### From source

JDK 21 or newer; Maven comes with the wrapper.

```sh
./mvnw verify                 # format check, unit tests, integration tests (needs Docker), jars
./mvnw verify -DskipITs       # without Docker
java -jar controller/target/controller.jar
java -jar simulated-device/target/simulated-device.jar
```

## Configuration

Everything comes from the environment. Connection strings never live in a file
in this repository.

| Variable | Process | Meaning |
|---|---|---|
| `TRANSPORT` | both | `mqtt` (default) or `azure` |
| `METRICS_PORT` | both | Port for `/metrics`, `/healthz`, `/readyz`; default `8080`, `0` disables |
| `HUMIDITY_THRESHOLD` | controller | Water below this soil humidity in percent; default `25` |
| `WATERING_COOLDOWN_SECONDS` | controller | Minimum time between two watering commands to one device; default `60` |
| `DEVICE_IDS` | device | Comma-separated plants to host; default: one named after the machine |
| `PLANT_NAMES`, `PLANT_INDEX` | device | For replicas: this replica hosts `PLANT_NAMES[PLANT_INDEX]` |
| `TELEMETRY_INTERVAL_SECONDS` | device | Seconds between readings at speed 1; default `5` |
| `ACTION_DURATION_SECONDS` | device | How long the pump and a reboot take at speed 1; default `5` |

MQTT transport:

| Variable | Meaning |
|---|---|
| `MQTT_HOST`, `MQTT_PORT` | Broker; default `localhost:1883` |
| `MQTT_TLS`, `MQTT_USERNAME`, `MQTT_PASSWORD` | Optional TLS and credentials |
| `MQTT_TOPIC_PREFIX` | First topic segment; default `garden` |
| `MQTT_COMMAND_TIMEOUT_SECONDS` | How long the controller waits for a device to answer; default `10` |

Azure transport, controller — Entra ID (preferred) or connection strings:

| Variable | Meaning |
|---|---|
| `AZURE_IOTHUB_HOSTNAME` | `<hub>.azure-devices.net`; authenticates with `DefaultAzureCredential` |
| `AZURE_EVENTHUB_NAMESPACE`, `AZURE_EVENTHUB_NAME` | The built-in endpoint, likewise |
| `IOTHUB_SERVICE_CONNECTION_STRING` | Fallback: shared access policy with *service connect* |
| `EVENTHUB_COMPATIBLE_CONNECTION_STRING` | Fallback: connection string of the built-in endpoint |
| `EVENTHUB_CONSUMER_GROUP` | Default `$Default` |

Azure transport, device: `IOTHUB_DEVICE_CONNECTION_STRING` (the device id is
taken from it) and optionally `IOTHUB_DEVICE_PROTOCOL` (`MQTT`, `MQTT_WS`,
`AMQPS`, `AMQPS_WS`).

## Commands a device understands

| Command | Payload | Effect |
|---|---|---|
| `water` | – | `202`; runs the pump, soaks the soil, reports `lastWater` |
| `reboot` | – | `202`; restarts, reports `lastReboot` |
| `setSpeed` | `{"factor": 25}` | Runs the simulation faster (0.1–100×) |
| `fault` | `{"type": "STUCK"}` | `NONE`, `STUCK`, `OVERREAD` or `SILENT` sensor |
| `status` | – | The simulator's view: true humidity, fault, speed, waterings |

Fleet commands (MQTT only, any device process picks them up): `addPlant`,
`removePlant`, `listPlants` with `{"deviceId": "thyme"}`.

## Engineering notes

- **Ports and adapters, for a reason.** The original IoT Hub deployment is long
  gone; putting the transports behind three small interfaces is what lets the
  same loop run against Mosquitto on a laptop and IoT Hub in Azure, and what
  makes the unit tests hub-free.
- **The wire contract is a record.** `Telemetry` in `common` is serialised and
  parsed with one codec that rejects malformed and out-of-range documents.
- **Identity over secrets.** On Azure the service side uses `DefaultAzureCredential`
  — managed identity in the cloud, the developer's login locally — with
  connection strings only as a fallback. Device identity comes from what the
  broker asserts (hub system property, MQTT topic), not from the message body.
- **Defensive loop.** Unreadable messages, failed twin updates and rejected
  commands are logged and skipped; long commands are acknowledged at once and
  executed off the callback thread. Both entry points shut down cleanly and the
  controller exits non-zero if its stream dies.
- **Tests at three levels.** Unit tests with injected clocks and fakes;
  integration tests that run the loop against a real broker; a CI smoke test
  that installs the chart on kind and waits for the first watering.
- **Supply chain.** Formatting enforced, coverage reported, CodeQL and Trivy in
  the Security tab, multi-arch images signed with Sigstore, Dependabot on Maven,
  Actions and base images.

## Status

Verified: the build, unit and integration tests; the compose stack; the Helm
chart on kind, with metrics scraped in-cluster; the MQTT transport end to end.
The Azure transport compiles against the current SDKs, its settings are unit
tested, and the Terraform configuration validates — but neither has been run
against a live subscription since the 2017 original. That is the next thing to
do with a free-tier hub.

## License

[Apache License 2.0](LICENSE).
