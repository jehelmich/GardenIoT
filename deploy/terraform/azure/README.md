# GardenIoT on Azure

Terraform for the real thing: an IoT Hub as the transport and both applications on Azure
Container Apps. Run it with [`scripts/deploy-azure.sh`](../../../scripts/deploy-azure.sh),
which checks the prerequisites and applies this configuration.

What it creates, in one resource group:

| Resource | Purpose |
|---|---|
| IoT Hub (F1, free) | the broker: device-to-cloud messages, direct methods, device twins |
| a device identity | the simulated plant, authenticating with its symmetric key |
| user-assigned managed identity | the controller's identity; *IoT Hub Data Contributor* for direct methods, *Azure Event Hubs Data Receiver* for the built-in endpoint |
| Container Apps environment + Log Analytics | where the two containers run and log |
| container app `controller` | `TRANSPORT=azure`, authenticates with the managed identity — no key in its configuration |
| container app `device` | `TRANSPORT=azure`, the device connection string as a Container Apps secret |

Images are pulled from GHCR, so a `v*` tag has to be released first (or pass
`-var image_tag=sha-…` for a CI build).

Notes:
- The device identity is created through the Azure CLI (`az iot hub device-identity`), which
  needs the `azure-iot` extension; Terraform's Azure provider only manages the control plane.
- The free F1 hub allows one per subscription and 8,000 messages a day; at the usual five-second
  interval one device would send 17,280, so `telemetry_interval_seconds` defaults to 15 here —
  or use `-var iot_hub_sku=B1`.
- This configuration has been validated (`terraform validate`) but, as of this writing, not
  applied against a live subscription. Expect to iterate on the role assignments if the built-in
  endpoint rejects the identity; the CLI output will say so.
