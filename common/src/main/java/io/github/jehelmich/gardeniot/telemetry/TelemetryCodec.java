package io.github.jehelmich.gardeniot.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Converts {@link Telemetry} to and from the JSON document carried in IoT Hub messages:
 *
 * <pre>{@code
 * {"deviceId":"garden-1","timestamp":"2017-07-17T10:15:30Z","temperature":22.4,"humidity":31.9}
 * }</pre>
 */
public final class TelemetryCodec {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
            .disableHtmlEscaping()
            .create();

    private TelemetryCodec() {
    }

    public static String toJson(Telemetry telemetry) {
        return GSON.toJson(telemetry);
    }

    /**
     * @throws IllegalArgumentException if the document is not valid JSON or does not describe a reading
     */
    public static Telemetry fromJson(String json) {
        Telemetry telemetry;
        try {
            telemetry = GSON.fromJson(json, Telemetry.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Malformed telemetry document: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Gson wraps whatever the record's constructor threw (missing field, value out of range).
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("Invalid telemetry document: " + cause.getMessage(), cause);
        }
        if (telemetry == null) {
            throw new IllegalArgumentException("Empty telemetry document");
        }
        return telemetry;
    }

    /** ISO-8601 instants are more useful to a human reading the hub than epoch millis. */
    private static final class InstantAdapter extends TypeAdapter<Instant> {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            String text = in.nextString();
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                throw new JsonParseException("Not an ISO-8601 instant: " + text, e);
            }
        }
    }
}
