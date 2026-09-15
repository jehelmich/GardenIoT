# Architecture

## Modules

```
common ◀──────── transport-mqtt ◀───┐
   ▲  ◀──────── transport-azure ◀──┼──── simulated-device
   │                                └──── controller
   └────────────────────────────────────── integration-tests (test scope, both apps)
```

`common` owns the vocabulary: the `Telemetry` record and its codec, the transport
ports, `Environment` for typed configuration, and the metrics/health server. The
two transport modules implement the ports. The applications depend on both
transports and choose at start-up; neither application class imports an SDK type.

## Ports

| Port | Side | MQTT adapter | Azure adapter |
|---|---|---|---|
| `DeviceTransportFactory` / `DeviceTransport` | device | one MQTT session per device, last will marks it offline | one `DeviceClient` per device |
| `CommandHandler` → `CommandResult` | device | request on `…/cmd/{name}`, reply on the request's response topic with its correlation data | direct method callback |
| `DeviceTransport.reportState` | device | retained `…/state/{name}` | reported twin property |
| `FleetChannel` | device | shared subscription `$share/fleet/…/_fleet/cmd/+` | not offered: hub devices must be registered |
| `DeviceProfileSource` | cloud | cache of retained `…/state/profile` topics | `TwinClient.get` → reported property `profile`, cached |
| `AlertPublisher` | cloud | retained `…/alert/{name}`, cleared by an empty message | `TwinClient.patch` → desired property `alerts.{name}` |
| `TelemetrySource` | cloud | subscription on `…/+/telemetry` | `EventHubConsumerAsyncClient` on the built-in endpoint |
| `DeviceCommandSender` | cloud | request/response with a per-client reply topic | `DirectMethodsClient.invoke` |

`CommandResult` deliberately mirrors a direct-method response (status code plus
JSON payload), so the MQTT reply body is `{"status":202,"payload":{…}}` and the
device code is identical on both transports.

## Topic layout (MQTT)

```
garden/{deviceId}/telemetry         readings, QoS 1
garden/{deviceId}/state/{name}      retained state ("device twin")
garden/{deviceId}/status            retained online/offline, set by the last will
garden/{deviceId}/alert/{name}      the controller's alerts, retained
garden/{deviceId}/cmd/{command}     command requests
garden/_fleet/cmd/{command}         fleet commands, shared subscription
garden/_reply/{clientId}            command replies for one cloud-side client
```

## The device process

```
DeviceApp ──▶ DeviceFleet ──▶ VirtualDevice ×N
                 │                 ├── PlantSimulation   (truth: species profile, health, growth)
                 │                 ├── WeatherProvider   (fixed / auto / Open-Meteo)
                 │                 ├── SensorFault       (what gets reported)
                 │                 ├── RandomFaults      (wear: sensor stuck/drift/silent, pump)
                 │                 ├── DeviceCommands    (CommandHandler)
                 │                 └── DeviceTransport
                 └── FleetChannel (addPlant / removePlant / listPlants / listProfiles)
```

Each `VirtualDevice` reschedules its own telemetry tick, so `setSpeed` takes
effect on the next reading; the pump and reboot run on a shared executor so the
transport's callback thread is never blocked. After every tick the device
reports `SimulationState` — true humidity, fault, speed, waterings — as state,
and exposes the same as gauges, which is how observers can see a sensor lie.

## The controller

```
TelemetrySource ──▶ TelemetryProcessor ──▶ WateringPolicy ──▶ WateringActuator ──▶ DeviceCommandSender
                          │      ▲                (per-plant threshold, cooldown, injected Clock)
                          │      └── DeviceProfileSource (what each plant said it needs)
                          ├──▶ AnomalyDetector ──▶ AlertPublisher   (stuck, ineffective, silent, pump)
                          └──▶ ControllerMetrics
```

The controller is stateless apart from the cooldown map, reads from "now", and
must run as a single instance: every replica would act on every reading.

## Observability

Every process serves `/metrics` (Prometheus), `/healthz` (liveness) and `/readyz`
(readiness, true once connected, false during shutdown) on `METRICS_PORT`.
Prometheus discovers device processes through Docker DNS in compose and pod
annotations in Kubernetes. The Grafana dashboard lives once, in the Helm chart,
and is mounted from there by compose.

## Deployment shapes

| Shape | Transport | Where | Entry point |
|---|---|---|---|
| Laptop | MQTT | Docker Compose | `docker compose up --build` |
| Cluster | MQTT (or Azure via values) | Helm chart, kind locally | `scripts/kind-up.sh` |
| Cloud | Azure IoT Hub | Terraform → Container Apps | `scripts/deploy-azure.sh` |

Images are built from one Dockerfile per module argument, run as a numeric
non-root user, and are published to GHCR by CI (multi-arch, Trivy-scanned,
Sigstore-signed).
