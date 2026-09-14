package io.github.jehelmich.gardeniot.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** The one Gson configuration used for everything that crosses a transport. */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(java.time.Instant.class, TelemetryCodec.INSTANT_ADAPTER)
            .disableHtmlEscaping()
            .serializeSpecialFloatingPointValues()
            .create();

    private Json() {}

    public static String stringify(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T parse(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    /**
     * @return the parsed tree, or {@code null} for a null, blank or missing document
     * @throws IllegalArgumentException if the text is not JSON
     */
    public static JsonElement tree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonNull() ? null : element;
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Not JSON: " + e.getMessage(), e);
        }
    }

    public static Gson gson() {
        return GSON;
    }
}
