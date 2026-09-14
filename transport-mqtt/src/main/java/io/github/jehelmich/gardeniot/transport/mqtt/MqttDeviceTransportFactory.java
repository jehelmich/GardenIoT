package io.github.jehelmich.gardeniot.transport.mqtt;

import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.DeviceTransport;
import io.github.jehelmich.gardeniot.transport.DeviceTransportFactory;
import io.github.jehelmich.gardeniot.transport.FleetChannel;
import java.util.Optional;

/** Connects devices to an MQTT broker; any device id is welcome, no registration needed. */
public final class MqttDeviceTransportFactory implements DeviceTransportFactory {

    private final MqttSettings settings;

    public MqttDeviceTransportFactory(MqttSettings settings) {
        this.settings = settings;
    }

    @Override
    public DeviceTransport connect(String deviceId, CommandHandler handler) throws Exception {
        return MqttDeviceTransport.open(settings, deviceId, handler);
    }

    @Override
    public Optional<FleetChannel> fleetChannel() {
        return Optional.of(new MqttFleetChannel(settings));
    }

    @Override
    public void close() {
        // Each device holds its own connection.
    }
}
