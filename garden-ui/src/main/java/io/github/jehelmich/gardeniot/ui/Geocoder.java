package io.github.jehelmich.gardeniot.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.telemetry.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/** Turns a place name into coordinates with Open-Meteo's free geocoding API. */
class Geocoder {

    /** A resolved place. */
    record Place(String name, double latitude, double longitude) {}

    private static final String ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search";
    private final HttpClient http;

    Geocoder(HttpClient http) {
        this.http = http;
    }

    Optional<Place> lookup(String query) throws IOException, InterruptedException {
        URI uri = URI.create(
                ENDPOINT + "?count=1&language=en&format=json&name=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Geocoding failed: HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    static Optional<Place> parse(String body) {
        JsonObject json = Json.tree(body).getAsJsonObject();
        JsonArray results = json.getAsJsonArray("results");
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        JsonObject first = results.get(0).getAsJsonObject();
        StringBuilder name = new StringBuilder(first.get("name").getAsString());
        if (first.has("country")) {
            name.append(", ").append(first.get("country").getAsString());
        }
        return Optional.of(new Place(
                name.toString(),
                first.get("latitude").getAsDouble(),
                first.get("longitude").getAsDouble()));
    }
}
