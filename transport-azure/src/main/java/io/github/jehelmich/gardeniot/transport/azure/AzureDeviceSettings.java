package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import io.github.jehelmich.gardeniot.config.Environment;

import java.util.Arrays;

/**
 * How a device authenticates to IoT Hub.
 *
 * <p>A device connection string carries a symmetric key, which is the appropriate credential
 * for a simulated device; a production device would use an X.509 certificate or the Device
 * Provisioning Service, both of which the SDK supports through the same client.
 *
 * @param connectionString {@code HostName=…;DeviceId=…;SharedAccessKey=…}
 * @param deviceId         the identity the connection string is for
 * @param protocol         MQTT by default; the WebSocket variants get through strict proxies
 */
public record AzureDeviceSettings(String connectionString, String deviceId, IotHubClientProtocol protocol) {

    public static final String CONNECTION_STRING = "IOTHUB_DEVICE_CONNECTION_STRING";
    public static final String PROTOCOL = "IOTHUB_DEVICE_PROTOCOL";

    public static AzureDeviceSettings fromEnvironment(Environment env) {
        String connectionString = env.required(CONNECTION_STRING);
        String protocol = env.optional(PROTOCOL, IotHubClientProtocol.MQTT.name());
        try {
            return new AzureDeviceSettings(connectionString, deviceIdOf(connectionString),
                    IotHubClientProtocol.valueOf(protocol.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(PROTOCOL + " must be one of "
                    + Arrays.toString(IotHubClientProtocol.values()) + ", was '" + protocol + "'");
        }
    }

    /** Extracts the {@code DeviceId} segment of a device connection string. */
    static String deviceIdOf(String connectionString) {
        return Arrays.stream(connectionString.split(";"))
                .map(String::strip)
                .filter(segment -> segment.regionMatches(true, 0, "DeviceId=", 0, "DeviceId=".length()))
                .map(segment -> segment.substring("DeviceId=".length()))
                .filter(id -> !id.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        CONNECTION_STRING + " does not contain a DeviceId segment"));
    }
}
