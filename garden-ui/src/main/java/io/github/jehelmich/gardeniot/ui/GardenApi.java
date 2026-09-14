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
 * POST   /api/plants                         {"deviceId": "thyme"}      addPlant on the fleet
 * DELETE /api/plants/{id}                                               removePlant on the fleet
 * POST   /api/plants/{id}/commands/{name}    JSON payload or empty      any device command
 * POST   /api/speed                          {"factor": 25}             setSpeed on every known plant
 * </pre>
 *
 * Responses carry the device's {@link CommandResult} as {@code {"status":…,"payload":…}}.
 */
final class GardenApi {

    /** The commands the page may send; anything else is refused before it reaches the bus. */
    static final Set<String> ALLOWED_COMMANDS = Set.of("water", "reboot", "setSpeed", "fault", "status");

    record Response(int httpStatus, String body) {}

    private final FleetCommandSender commands;
    private final GardenModel model;

    GardenApi(FleetCommandSender commands, GardenModel model) {
        this.commands = commands;
        this.model = model;
    }

    Response handle(String method, String path, String body) {
        try {
            String[] parts = path.split("/");
            // parts: "", "api", ...
            if (parts.length == 3 && parts[2].equals("plants") && method.equals("POST")) {
                return addPlant(body);
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
            return error(404, "No such endpoint");
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        } catch (CommandException e) {
            return error(504, e.getMessage());
        }
    }

    private Response addPlant(String body) throws CommandException {
        String deviceId = deviceIdIn(body);
        return reply(commands.sendToFleet("addPlant", Map.of("deviceId", deviceId)));
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

    private Response speed(String body) throws CommandException {
        JsonElement payload = Json.tree(body);
        if (!(payload instanceof JsonObject object) || object.get("factor") == null) {
            throw new IllegalArgumentException("Expected {\"factor\": <number>}");
        }
        List<JsonObject> results = new ArrayList<>();
        for (String deviceId : model.deviceIds()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("deviceId", deviceId);
            try {
                CommandResult result = commands.send(deviceId, "setSpeed", object);
                entry.addProperty("status", result.status());
            } catch (CommandException e) {
                entry.addProperty("status", 504);
                entry.addProperty("message", e.getMessage());
            }
            results.add(entry);
        }
        return new Response(200, Json.stringify(results));
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
