package io.github.jehelmich.gardeniot.device;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;

/**
 * The commands a plant answers to.
 *
 * <pre>
 * water                       run the pump; 202, completion reported as state
 * reboot                      restart; 202, completion reported as state
 * setSpeed {"factor": 10}     run the simulation faster or slower; 200
 * fault    {"type": "STUCK"}  break (or fix) the humidity sensor; 200
 * status                      the simulator's view of the plant; 200
 * </pre>
 *
 * Long-running commands are acknowledged immediately and completed by the {@link VirtualDevice}
 * on its own thread, as a real device would.
 */
final class DeviceCommands implements CommandHandler {

    static final String WATER = "water";
    static final String REBOOT = "reboot";
    static final String SET_SPEED = "setSpeed";
    static final String FAULT = "fault";
    static final String STATUS = "status";

    static final double MIN_SPEED = 0.1;
    static final double MAX_SPEED = 100.0;

    private static final Logger log = LoggerFactory.getLogger(DeviceCommands.class);

    private final VirtualDevice device;

    DeviceCommands(VirtualDevice device) {
        this.device = device;
    }

    @Override
    public CommandResult handle(String command, String payloadJson) {
        log.info("{}: command '{}' {}", device.deviceId(), command, payloadJson == null ? "" : payloadJson);
        JsonElement payload;
        try {
            payload = Json.tree(payloadJson);
        } catch (IllegalArgumentException e) {
            return CommandResult.badRequest(e.getMessage());
        }
        return switch (command) {
            case WATER -> {
                device.startWatering();
                yield CommandResult.accepted("Started watering");
            }
            case REBOOT -> {
                device.startReboot();
                yield CommandResult.accepted("Started reboot");
            }
            case SET_SPEED -> setSpeed(payload);
            case FAULT -> fault(payload);
            case STATUS -> CommandResult.ok(device.state());
            default -> CommandResult.notFound(command);
        };
    }

    private CommandResult setSpeed(JsonElement payload) {
        JsonElement factor = field(payload, "factor");
        if (factor == null || !factor.isJsonPrimitive() || !factor.getAsJsonPrimitive().isNumber()) {
            return CommandResult.badRequest("Expected {\"factor\": <number>}");
        }
        double speed = factor.getAsDouble();
        if (speed < MIN_SPEED || speed > MAX_SPEED) {
            return CommandResult.badRequest("factor must be within " + MIN_SPEED + ".." + MAX_SPEED);
        }
        device.setSpeed(speed);
        return CommandResult.ok(device.state());
    }

    private CommandResult fault(JsonElement payload) {
        JsonElement type = field(payload, "type");
        if (type == null || !type.isJsonPrimitive()) {
            return CommandResult.badRequest("Expected {\"type\": one of " + Arrays.toString(SensorFault.values()) + "}");
        }
        try {
            device.setFault(SensorFault.valueOf(type.getAsString().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return CommandResult.badRequest("type must be one of " + Arrays.toString(SensorFault.values()));
        }
        return CommandResult.ok(device.state());
    }

    private static JsonElement field(JsonElement payload, String name) {
        return payload instanceof JsonObject object ? object.get(name) : null;
    }
}
