package io.github.jehelmich.gardeniot.device;

import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.device.WeatherConditions.Kind;
import io.github.jehelmich.gardeniot.telemetry.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live weather for a place, from the free <a href="https://open-meteo.com/">Open-Meteo</a> API
 * (no key needed). Refreshed every ten minutes; if the service is unreachable the last known
 * conditions are kept, or clear weather is assumed until it answers.
 */
public final class OpenMeteoWeather implements WeatherProvider {

    static final Duration REFRESH = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeather.class);
    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast";

    private final double latitude;
    private final double longitude;
    private final String place;
    private final HttpClient http;
    private final Clock clock;
    private volatile WeatherConditions cached;
    private volatile Instant fetchedAt = Instant.EPOCH;

    public OpenMeteoWeather(double latitude, double longitude, String place, HttpClient http, Clock clock) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Not a location: " + latitude + ", " + longitude);
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.place = place == null || place.isBlank()
                ? String.format(Locale.ROOT, "%.2f, %.2f", latitude, longitude)
                : place;
        this.http = http;
        this.clock = clock;
    }

    public String place() {
        return place;
    }

    @Override
    public WeatherConditions current() {
        Instant now = clock.instant();
        if (cached == null || Duration.between(fetchedAt, now).compareTo(REFRESH) >= 0) {
            synchronized (this) {
                if (cached == null || Duration.between(fetchedAt, now).compareTo(REFRESH) >= 0) {
                    fetchedAt = now;
                    refresh();
                }
            }
        }
        return cached != null ? cached : WeatherConditions.CLEAR.withSource(place + " (offline)");
    }

    private void refresh() {
        URI uri = URI.create(String.format(
                Locale.ROOT,
                "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,precipitation,weather_code,wind_speed_10m",
                ENDPOINT,
                latitude,
                longitude));
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            cached = fromCurrent(Json.tree(response.body()).getAsJsonObject().getAsJsonObject("current"), place);
            log.info("Weather for {}: {}", place, cached.label());
        } catch (IOException | RuntimeException e) {
            log.warn("Could not fetch weather for {}: {}", place, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Maps an Open-Meteo "current" block onto what the pot feels. */
    static WeatherConditions fromCurrent(JsonObject current, String place) {
        double temperature = current.get("temperature_2m").getAsDouble();
        double precipitation =
                current.has("precipitation") ? current.get("precipitation").getAsDouble() : 0; // mm/h
        double wind =
                current.has("wind_speed_10m") ? current.get("wind_speed_10m").getAsDouble() : 0; // km/h
        int code = current.has("weather_code") ? current.get("weather_code").getAsInt() : 0;
        Kind kind = kindOf(code, temperature);
        double rain = precipitation * 0.3;
        double evaporation =
                switch (kind) {
                            case RAIN, STORM, SNOW, FOG -> 0.5;
                            case CLOUDY -> 0.8;
                            default -> 1.0 + Math.max(0, temperature - 25) / 10.0;
                        }
                        * (1.0 + Math.min(wind, 60) / 100.0);
        String label = String.format(
                Locale.ROOT,
                "%s, %.0f °C%s",
                describe(code),
                temperature,
                precipitation > 0 ? String.format(Locale.ROOT, ", %.1f mm/h", precipitation) : "");
        return new WeatherConditions(kind, label, place, temperature, rain, evaporation);
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    static Kind kindOf(int code, double temperature) {
        if (code >= 95) return Kind.STORM;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Kind.SNOW;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return Kind.RAIN;
        if (code == 45 || code == 48) return Kind.FOG;
        if (temperature >= 33) return Kind.HEATWAVE;
        if (temperature <= 4) return Kind.COLD;
        if (code >= 2) return Kind.CLOUDY;
        return Kind.CLEAR;
    }

    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }
}
