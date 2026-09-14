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
 * @param weather           the weather every plant starts under; see {@link WeatherProviders}
 * @param wearMeanTicks     average readings between random breakages; 0 for none
 */
public record DeviceConfig(
        Transport transport,
        List<String> deviceIds,
        Duration telemetryInterval,
        Duration actionDuration,
        WeatherProviders.Setting weather,
        long wearMeanTicks) {

    public static final String DEVICE_IDS = "DEVICE_IDS";
    /** For replicated deployments: a list of names and this replica's index into it. */
    public static final String PLANT_NAMES = "PLANT_NAMES";

    public static final String PLANT_INDEX = "PLANT_INDEX";
    public static final String TELEMETRY_INTERVAL = "TELEMETRY_INTERVAL_SECONDS";
    public static final String ACTION_DURATION = "ACTION_DURATION_SECONDS";
    public static final String WEATHER = "WEATHER";
    public static final String WEATHER_LATITUDE = "WEATHER_LATITUDE";
    public static final String WEATHER_LONGITUDE = "WEATHER_LONGITUDE";
    public static final String WEATHER_PLACE = "WEATHER_PLACE";
    public static final String WEAR_MEAN_TICKS = "WEAR_MEAN_TICKS";

    public static DeviceConfig fromEnvironment(Environment env) {
        return new DeviceConfig(
                Transport.fromEnvironment(env),
                deviceIds(env),
                env.optionalSeconds(TELEMETRY_INTERVAL, Duration.ofSeconds(5)),
                env.optionalSeconds(ACTION_DURATION, Duration.ofSeconds(5)),
                new WeatherProviders.Setting(
                        env.optional(WEATHER, "clear"),
                        optionalNumber(env, WEATHER_LATITUDE),
                        optionalNumber(env, WEATHER_LONGITUDE),
                        env.optional(WEATHER_PLACE, null)),
                (long) env.optionalDouble(WEAR_MEAN_TICKS, 0));
    }

    private static Double optionalNumber(Environment env, String name) {
        double value = env.optionalDouble(name, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    /**
     * {@code DEVICE_IDS} wins. Otherwise, a replica that knows its index picks that entry of
     * {@code PLANT_NAMES} — replica 0 of "basil,mint" hosts basil — and an index beyond the list
     * means "no preset plant", leaving the default hostname-derived id to the application.
     */
    static List<String> deviceIds(Environment env) {
        List<String> ids = parseIds(env.optional(DEVICE_IDS, ""));
        if (!ids.isEmpty()) {
            return ids;
        }
        List<String> names = parseIds(env.optional(PLANT_NAMES, ""));
        int index = (int) env.optionalDouble(PLANT_INDEX, -1);
        if (index >= 0 && index < names.size()) {
            return List.of(names.get(index));
        }
        return List.of();
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
            throw new IllegalArgumentException(
                    "Device id '" + id + "' must be 1-64 characters of letters, digits, '.', '_' or '-'");
        }
    }
}
