package io.github.jehelmich.gardeniot.transport;

import java.util.Optional;

/** Opens connections for device identities. There is one factory per transport technology. */
public interface DeviceTransportFactory extends AutoCloseable {

    /**
     * Connects a device and starts delivering its commands to {@code handler}.
     *
     * @throws Exception if the connection cannot be established
     */
    DeviceTransport connect(String deviceId, CommandHandler handler) throws Exception;

    /**
     * A channel for commands addressed to the fleet as a whole rather than to one device, such
     * as "add a plant". Only transports where devices can appear without prior registration offer
     * one; IoT Hub does not, because every device identity must exist in the hub first.
     */
    default Optional<FleetChannel> fleetChannel() {
        return Optional.empty();
    }

    @Override
    void close();
}
