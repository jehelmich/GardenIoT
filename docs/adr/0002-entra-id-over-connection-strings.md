# 0002 — Prefer Entra ID over connection strings on Azure

**Status:** accepted, 2026-09

## Context

The original controller carried the hub's *iothubowner* connection string as a
constant. Shared access keys are long-lived, hard to rotate, and end up in
environment dumps and logs.

## Decision

The service side (controller) authenticates with `DefaultAzureCredential`: a
managed identity in Azure, the developer's own CLI login on a workstation. It is
given exactly the two roles it needs — *IoT Hub Data Contributor* for direct
methods and *Azure Event Hubs Data Receiver* for the built-in endpoint. The
Terraform configuration creates that identity and the role assignments.
Connection strings remain available as a fallback for environments without an
identity.

The device keeps a device credential (a symmetric key in the connection string):
that is how devices authenticate to IoT Hub, and it scopes to one identity. A
production device would use X.509 or the Device Provisioning Service through
the same client.

## Consequences

- No key appears in the controller's configuration, in Terraform state, or in a
  Kubernetes secret when workload identity is used.
- One `TokenCredential` instance is shared per process; the chain probes its
  environment once.
- The identity's roles have been validated only by Terraform's schema, not
  against a live hub; the module README says so.
