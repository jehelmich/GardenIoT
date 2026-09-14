package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.DeviceTransport;
import io.github.jehelmich.gardeniot.transport.DeviceTransportFactory;
import java.util.List;

/**
 * Connects the one device identity a device connection string is for.
 *
 * <p>IoT Hub has no notion of an unregistered device, so this factory offers no
 * {@linkplain #fleetChannel() fleet channel}: adding a plant means registering a device in the
 * hub and starting a process with its connection string.
 */
public final class AzureDeviceTransportFactory implements DeviceTransportFactory {

    private final AzureDeviceSettings settings;

    public AzureDeviceTransportFactory(AzureDeviceSettings settings) {
        this.settings = settings;
    }

    /** The device identity the connection string authenticates. */
    public List<String> boundDeviceIds() {
        return List.of(settings.deviceId());
    }

    @Override
    public DeviceTransport connect(String deviceId, CommandHandler handler) throws Exception {
        if (!settings.deviceId().equals(deviceId)) {
            throw new IllegalArgumentException(
                    "The connection string authenticates '" + settings.deviceId() + "', not '" + deviceId + "'");
        }
        DeviceClient client = new DeviceClient(settings.connectionString(), settings.protocol());
        return AzureDeviceTransport.open(client, deviceId, handler);
    }

    @Override
    public void close() {
        // Nothing shared between connections.
    }
}
