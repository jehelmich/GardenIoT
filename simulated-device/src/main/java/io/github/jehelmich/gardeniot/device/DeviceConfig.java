package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.config.Environment;

import java.time.Duration;
import java.util.Arrays;

/**
 * Runtime settings for the simulated device.
 *
 * @param connectionString  the device's IoT Hub connection string
 * @param deviceId          the device identity, taken from the connection string
 * @param telemetryInterval how often a reading is published
 * @param actionDuration    how long the simulated pump and reboot take
 */
public record DeviceConfig(String connectionString,
                           String deviceId,
                           Duration telemetryInterval,
                           Duration actionDuration) {

    public static final String CONNECTION_STRING = "IOTHUB_DEVICE_CONNECTION_STRING";
    public static final String TELEMETRY_INTERVAL = "TELEMETRY_INTERVAL_SECONDS";
    public static final String ACTION_DURATION = "ACTION_DURATION_SECONDS";

    public static DeviceConfig fromEnvironment(Environment env) {
        String connectionString = env.required(CONNECTION_STRING);
        return new DeviceConfig(
                connectionString,
                deviceIdOf(connectionString),
                env.optionalSeconds(TELEMETRY_INTERVAL, Duration.ofSeconds(5)),
                env.optionalSeconds(ACTION_DURATION, Duration.ofSeconds(5)));
    }

    /**
     * Extracts the {@code DeviceId} segment of an IoT Hub device connection string
     * ({@code HostName=...;DeviceId=...;SharedAccessKey=...}).
     */
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
