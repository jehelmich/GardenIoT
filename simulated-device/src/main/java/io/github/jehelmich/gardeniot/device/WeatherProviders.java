package io.github.jehelmich.gardeniot.device;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Builds a {@link WeatherProvider} from a setting — the {@code WEATHER} variables at start-up or
 * a {@code setWeather} command later:
 *
 * <pre>
 * clear | rain | drought | heatwave | cold     fixed conditions
 * auto                                         changes every couple of minutes, offline
 * real  + latitude, longitude [, place]        live from Open-Meteo
 * </pre>
 */
public final class WeatherProviders {

    /** A weather setting as configured or requested. */
    public record Setting(String mode, Double latitude, Double longitude, String place) {

        public static Setting of(String mode) {
            return new Setting(mode, null, null, null);
        }

        static Setting fromJson(JsonElement payload) {
            if (!(payload instanceof JsonObject object)
                    || !object.has("mode")
                    || !object.get("mode").isJsonPrimitive()) {
                throw new IllegalArgumentException(
                        "Expected {\"mode\": clear|rain|drought|heatwave|cold|auto|real, ...}");
            }
            return new Setting(
                    object.get("mode").getAsString(),
                    number(object, "latitude"),
                    number(object, "longitude"),
                    object.has("place") && object.get("place").isJsonPrimitive()
                            ? object.get("place").getAsString()
                            : null);
        }

        private static Double number(JsonObject object, String name) {
            JsonElement value = object.get(name);
            if (value == null
                    || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            return value.getAsDouble();
        }
    }

    private final Clock clock;
    private final HttpClient http;

    public WeatherProviders(Clock clock) {
        this(
                clock,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    WeatherProviders(Clock clock, HttpClient http) {
        this.clock = clock;
        this.http = http;
    }

    /**
     * @throws IllegalArgumentException for an unknown mode or a live request without coordinates
     */
    public WeatherProvider create(Setting setting) {
        String mode = setting.mode() == null ? "clear" : setting.mode().strip().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "auto" -> WeatherProvider.auto(clock);
            case "real", "live" -> {
                if (setting.latitude() == null || setting.longitude() == null) {
                    throw new IllegalArgumentException("Live weather needs latitude and longitude");
                }
                yield new OpenMeteoWeather(setting.latitude(), setting.longitude(), setting.place(), http, clock);
            }
            default ->
                WeatherProvider.fixed(WeatherProvider.fixedMode(mode)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Weather mode must be clear, rain, drought, heatwave, cold, auto or real; was '" + mode
                                        + "'")));
        };
    }
}
