# 0003 — Read the built-in endpoint with a plain consumer client

**Status:** accepted, 2026-09

## Context

The Azure SDK offers two ways to read an event hub: `EventHubConsumerAsyncClient`
(subscribe to partitions, no state) and `EventProcessorClient` (load balancing
across instances and checkpointing through a blob store).

## Decision

Use the consumer client, reading every partition from "now".

## Consequences

- No storage account, no checkpoint store, one fewer moving part in Terraform.
- The controller cares only about the latest reading per device; an event it
  missed while restarting is superseded by the next one seconds later, so
  checkpointing would buy nothing.
- If the controller ever needed to scale out or replay, the processor client is
  the drop-in replacement behind the same `TelemetrySource` port.
