package io.github.jehelmich.gardeniot.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.CommandException;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.FleetCommandSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The page's write side, translated into device and fleet commands:
 *
 * <pre>
 * GET    /api/profiles                                                  the species the fleet offers
 * POST   /api/plants                         {"deviceId": "thyme", "profile": "mint"}   addPlant on the fleet
 * DELETE /api/plants/{id}                                               removePlant on the fleet
 * POST   /api/plants/{id}/commands/{name}    JSON payload or empty      any allowed device command
 * POST   /api/speed                          {"factor": 25}             setSpeed on every known plant
 * POST   /api/wear                           {"meanTicks": 1500}        setWear on every known plant
 * POST   /api/weather                        {"mode": "rain"} or {"mode": "real", "place": "Lisbon"}
 *                                                                       setWeather on every known plant;
 *                                                                       a place is geocoded first
 * </pre>
 *
 * Responses carry the device's {@link CommandResult} as {@code {"status":…,"payload":…}}.
 */
final class GardenApi {

    /** The commands the page may send; anything else is refused before it reaches the bus. */
    static final Set<String> ALLOWED_COMMANDS = Set.of(
            "water",
            "reboot",
            "callTechnician",
            "repot",
            "setSpeed",
            "fault",
            "pump",
            "setWeather",
            "setWear",
            "status");

    record Response(int httpStatus, String body) {}

    private final FleetCommandSender commands;
    private final GardenModel model;
    private final Geocoder geocoder;

    GardenApi(FleetCommandSender commands, GardenModel model) {
        this(commands, model, new Geocoder(java.net.http.HttpClient.newHttpClient()));
    }

    GardenApi(FleetCommandSender commands, GardenModel model, Geocoder geocoder) {
        this.commands = commands;
        this.model = model;
        this.geocoder = geocoder;
    }

    Response handle(String method, String path, String body) {
        try {
            String[] parts = path.split("/");
            // parts: "", "api", ...
            if (parts.length == 3 && parts[2].equals("profiles") && method.equals("GET")) {
                return reply(commands.sendToFleet("listProfiles", null));
            }
            if (parts.length == 3 && parts[2].equals("plants") && method.equals("POST")) {
                return addPlant(body);
            }
            if (parts.length == 3 && parts[2].equals("weather") && method.equals("POST")) {
                return weather(body);
            }
            if (parts.length == 4 && parts[2].equals("plants") && method.equals("DELETE")) {
                return removePlant(parts[3]);
            }
            if (parts.length == 6
                    && parts[2].equals("plants")
                    && parts[4].equals("commands")
                    && method.equals("POST")) {
                return command(parts[3], parts[5], body);
            }
            if (parts.length == 3 && parts[2].equals("speed") && method.equals("POST")) {
                return speed(body);
            }
            if (parts.length == 3 && parts[2].equals("wear") && method.equals("POST")) {
                JsonElement payload = Json.tree(body);
                if (!(payload instanceof JsonObject object) || object.get("meanTicks") == null) {
                    throw new IllegalArgumentException("Expected {\"meanTicks\": <number>}");
                }
                return broadcast("setWear", object);
            }
            return error(404, "No such endpoint");
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        } catch (CommandException e) {
            return error(504, e.getMessage());
        } catch (java.io.IOException e) {
            return error(502, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(500, "Interrupted");
        }
    }

    private Response addPlant(String body) throws CommandException {
        String deviceId = deviceIdIn(body);
        JsonElement payload = Json.tree(body);
        Map<String, String> request = new java.util.HashMap<>(Map.of("deviceId", deviceId));
        if (payload instanceof JsonObject object
                && object.get("profile") != null
                && object.get("profile").isJsonPrimitive()) {
            request.put("profile", object.get("profile").getAsString());
        }
        return reply(commands.sendToFleet("addPlant", request));
    }

    /** Resolves a place name to coordinates, then tells every plant about the weather. */
    private Response weather(String body) throws CommandException, java.io.IOException, InterruptedException {
        JsonElement payload = Json.tree(body);
        if (!(payload instanceof JsonObject object) || object.get("mode") == null) {
            throw new IllegalArgumentException("Expected {\"mode\": ...}");
        }
        JsonObject setting = object.deepCopy();
        if (object.get("mode").getAsString().equalsIgnoreCase("real")
                && object.get("place") != null
                && (object.get("latitude") == null || object.get("longitude") == null)) {
            Geocoder.Place place = geocoder.lookup(object.get("place").getAsString())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No such place: " + object.get("place").getAsString()));
            setting.addProperty("place", place.name());
            setting.addProperty("latitude", place.latitude());
            setting.addProperty("longitude", place.longitude());
        }
        return broadcast("setWeather", setting);
    }

    private Response removePlant(String deviceId) throws CommandException {
        CommandResult result = commands.sendToFleet("removePlant", Map.of("deviceId", deviceId));
        if (result.isSuccess()) {
            model.forget(deviceId);
        }
        return reply(result);
    }

    private Response command(String deviceId, String command, String body) throws CommandException {
        if (!ALLOWED_COMMANDS.contains(command)) {
            throw new IllegalArgumentException("Command must be one of " + ALLOWED_COMMANDS);
        }
        return reply(commands.send(deviceId, command, Json.tree(body)));
    }

    private Response speed(String body) {
        JsonElement payload = Json.tree(body);
        if (!(payload instanceof JsonObject object) || object.get("factor") == null) {
            throw new IllegalArgumentException("Expected {\"factor\": <number>}");
        }
        return broadcast("setSpeed", object);
    }

    /** Sends one command to every known plant; each answer is reported, none aborts the rest. */
    private Response broadcast(String command, JsonObject payload) {
        List<JsonObject> results = new ArrayList<>();
        int failures = 0;
        for (String deviceId : model.deviceIds()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("deviceId", deviceId);
            try {
                CommandResult result = commands.send(deviceId, command, payload);
                entry.addProperty("status", result.status());
                if (!result.isSuccess()) {
                    failures++;
                    entry.add("payload", Json.gson().toJsonTree(result.payload()));
                }
            } catch (CommandException e) {
                failures++;
                entry.addProperty("status", 504);
                entry.addProperty("message", e.getMessage());
            }
            results.add(entry);
        }
        JsonObject json = new JsonObject();
        json.addProperty("status", failures == 0 ? 200 : 207);
        json.add("results", Json.gson().toJsonTree(results));
        if (failures > 0) {
            json.addProperty("message", failures + " of " + results.size() + " plants did not accept " + command);
        }
        return new Response(200, json.toString());
    }

    private static String deviceIdIn(String body) {
        JsonElement payload = Json.tree(body);
        if (payload instanceof JsonObject object
                && object.get("deviceId") != null
                && object.get("deviceId").isJsonPrimitive()) {
            String id = object.get("deviceId").getAsString().strip();
            if (id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
                return id;
            }
            throw new IllegalArgumentException("Plant names are 1-64 letters, digits, '.', '_' or '-'");
        }
        throw new IllegalArgumentException("Expected {\"deviceId\": \"...\"}");
    }

    private static Response reply(CommandResult result) {
        JsonObject json = new JsonObject();
        json.addProperty("status", result.status());
        json.add("payload", Json.gson().toJsonTree(result.payload()));
        return new Response(result.isSuccess() ? 200 : 502, json.toString());
    }

    private static Response error(int status, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("status", status);
        json.addProperty("message", message);
        return new Response(status, json.toString());
    }
}
