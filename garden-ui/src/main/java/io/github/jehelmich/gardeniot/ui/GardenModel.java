package io.github.jehelmich.gardeniot.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver.BusMessage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * What the page knows about the garden: the last reading, state and presence of every plant,
 * plus the commands seen on the bus. Every change is turned into a JSON event for the
 * listeners (the SSE connections), and a newcomer gets a snapshot first.
 */
public final class GardenModel {

    /** Everything known about one plant. */
    public static final class Plant {
        private final String deviceId;
        private boolean online;
        private Telemetry telemetry;
        private final Map<String, JsonElement> state = new TreeMap<>();
        private String lastCommand;
        private Instant lastCommandAt;

        Plant(String deviceId) {
            this.deviceId = deviceId;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("deviceId", deviceId);
            json.addProperty("online", online);
            json.add("telemetry", telemetry == null ? null : Json.gson().toJsonTree(telemetry));
            JsonObject stateJson = new JsonObject();
            state.forEach(stateJson::add);
            json.add("state", stateJson);
            json.addProperty("lastCommand", lastCommand);
            json.addProperty("lastCommandAt", lastCommandAt == null ? null : lastCommandAt.toString());
            return json;
        }
    }

    private final Clock clock;
    private final Map<String, Plant> plants = new TreeMap<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public GardenModel(Clock clock) {
        this.clock = clock;
    }

    /** Applies a bus message and tells the listeners. */
    public void apply(BusMessage message) {
        String event;
        synchronized (this) {
            event = switch (message.kind()) {
                case TELEMETRY -> telemetry(message);
                case STATE -> state(message);
                case STATUS -> status(message);
                case COMMAND -> command(message);
                case FLEET_COMMAND -> fleet(message);
            };
        }
        if (event != null) {
            listeners.forEach(listener -> listener.accept(event));
        }
    }

    private String telemetry(BusMessage message) {
        Telemetry telemetry;
        try {
            telemetry = TelemetryCodec.fromJson(message.payload());
        } catch (IllegalArgumentException e) {
            return null;
        }
        Plant plant = plant(message.deviceId());
        plant.telemetry = telemetry;
        plant.online = true;
        return event("telemetry", message.deviceId(), "telemetry", Json.gson().toJsonTree(telemetry));
    }

    private String state(BusMessage message) {
        JsonElement value;
        try {
            value = Json.tree(message.payload());
        } catch (IllegalArgumentException e) {
            return null;
        }
        Plant plant = plant(message.deviceId());
        if (value == null) {
            plant.state.remove(message.name());
        } else {
            plant.state.put(message.name(), value);
        }
        JsonObject event = eventObject("state", message.deviceId());
        event.addProperty("name", message.name());
        event.add("value", value);
        return event.toString();
    }

    private String status(BusMessage message) {
        Plant plant = plant(message.deviceId());
        plant.online = "online".equals(message.payload());
        JsonObject event = eventObject("status", message.deviceId());
        event.addProperty("online", plant.online);
        return event.toString();
    }

    private String command(BusMessage message) {
        Plant plant = plant(message.deviceId());
        plant.lastCommand = message.name();
        plant.lastCommandAt = clock.instant();
        JsonObject event = eventObject("command", message.deviceId());
        event.addProperty("command", message.name());
        event.add("payload", safeTree(message.payload()));
        return event.toString();
    }

    private String fleet(BusMessage message) {
        JsonObject event = eventObject("fleet", null);
        event.addProperty("command", message.name());
        event.add("payload", safeTree(message.payload()));
        return event.toString();
    }

    private Plant plant(String deviceId) {
        return plants.computeIfAbsent(deviceId, Plant::new);
    }

    /** Drops a plant the fleet has removed; its retained topics may still say goodbye later. */
    public void forget(String deviceId) {
        String event;
        synchronized (this) {
            if (plants.remove(deviceId) == null) {
                return;
            }
            event = eventObject("removed", deviceId).toString();
        }
        listeners.forEach(listener -> listener.accept(event));
    }

    public synchronized List<String> deviceIds() {
        return new ArrayList<>(plants.keySet());
    }

    /** The full picture, for a client that has just connected. */
    public synchronized String snapshot() {
        JsonObject event = eventObject("snapshot", null);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        plants.values().forEach(plant -> array.add(plant.toJson()));
        event.add("plants", array);
        return event.toString();
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    private String event(String type, String deviceId, String field, JsonElement value) {
        JsonObject event = eventObject(type, deviceId);
        event.add(field, value);
        return event.toString();
    }

    private JsonObject eventObject(String type, String deviceId) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        event.addProperty("deviceId", deviceId);
        event.addProperty("at", clock.instant().toString());
        return event;
    }

    private static JsonElement safeTree(String json) {
        try {
            return Json.tree(json);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
