package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.config.Transport;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Runtime settings for the device process; the transport reads its own on top of these.
 *
 * @param transport         how to reach the cloud side
 * @param deviceIds         the plants this process starts with; empty means "let the transport decide"
 * @param telemetryInterval how often a reading is published at simulation speed 1
 * @param actionDuration    how long the simulated pump and reboot take at speed 1
 */
public record DeviceConfig(Transport transport,
                           List<String> deviceIds,
                           Duration telemetryInterval,
                           Duration actionDuration) {

    public static final String DEVICE_IDS = "DEVICE_IDS";
    public static final String TELEMETRY_INTERVAL = "TELEMETRY_INTERVAL_SECONDS";
    public static final String ACTION_DURATION = "ACTION_DURATION_SECONDS";

    public static DeviceConfig fromEnvironment(Environment env) {
        return new DeviceConfig(
                Transport.fromEnvironment(env),
                parseIds(env.optional(DEVICE_IDS, "")),
                env.optionalSeconds(TELEMETRY_INTERVAL, Duration.ofSeconds(5)),
                env.optionalSeconds(ACTION_DURATION, Duration.ofSeconds(5)));
    }

    static List<String> parseIds(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(","))
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .peek(DeviceConfig::requireValidId)
                .toList();
    }

    /** Device ids end up in topic names and URLs, so keep them to a safe alphabet. */
    static void requireValidId(String id) {
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Device id '" + id
                    + "' must be 1-64 characters of letters, digits, '.', '_' or '-'");
        }
    }
}
