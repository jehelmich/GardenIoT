# Architecture decision records

Short records of the decisions that shaped this repository, in the order they were made.

| # | Decision |
|---|---|
| [0001](0001-transports-behind-ports.md) | Put the transports behind ports and add an MQTT profile |
| [0002](0002-entra-id-over-connection-strings.md) | Prefer Entra ID over connection strings on Azure |
| [0003](0003-consumer-client-not-event-processor.md) | Read the built-in endpoint with a plain consumer client |
| [0004](0004-cooldown-in-the-controller.md) | Keep the watering cooldown in the controller |
| [0005](0005-single-controller.md) | Run one controller |
| [0006](0006-simulator-reports-ground-truth.md) | Let the simulator report its ground truth |
| [0007](0007-plant-needs-on-the-twin.md) | A plant's needs travel as reported device state |
| [0008](0008-alerts-from-the-outside.md) | Detect device faults from the outside, and say so on the twin |
