package io.github.jehelmich.gardeniot.device;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The commands a plant answers to.
 *
 * <pre>
 * water                              run the pump; 202 (500 if the pump reports no flow)
 * reboot                             restart; 202, completion reported as state
 * callTechnician                     sensor and pump serviced after a while; 202 (409 if a job is under way)
 * setWear    {"meanTicks": 1500}     how often things break on their own; 0 never; 200
 * pump       {"failed": true}        break or fix the pump; 200
 * repot                              a fresh seedling in the pot; 202 (409 if a job is under way)
 * setSpeed   {"factor": 10}          run the simulation faster or slower; 200
 * fault      {"type": "STUCK"}       break (or fix) the humidity sensor; 200
 * setWeather {"mode": "rain"}        fixed weather, "auto", or "real" with latitude/longitude/place; 200
 * status                             the simulator's view of the plant; 200
 * </pre>
 *
 * Long-running commands are acknowledged immediately and completed by the {@link VirtualDevice}
 * on its own thread, as a real device would.
 */
final class DeviceCommands implements CommandHandler {

    static final String WATER = "water";
    static final String REBOOT = "reboot";
    static final String CALL_TECHNICIAN = "callTechnician";
    /** The older name for {@link #CALL_TECHNICIAN}; still answered. */
    static final String REPAIR_SENSOR = "repairSensor";

    static final String SET_WEAR = "setWear";
    static final String PUMP = "pump";
    static final String REPOT = "repot";
    static final String SET_SPEED = "setSpeed";
    static final String FAULT = "fault";
    static final String SET_WEATHER = "setWeather";
    static final String STATUS = "status";

    static final double MIN_SPEED = 0.1;
    static final double MAX_SPEED = 100.0;
    static final int CONFLICT = 409;

    private static final Logger log = LoggerFactory.getLogger(DeviceCommands.class);

    private final VirtualDevice device;
    private final WeatherProviders weathers;

    DeviceCommands(VirtualDevice device) {
        this(device, new WeatherProviders(java.time.Clock.systemUTC()));
    }

    DeviceCommands(VirtualDevice device, WeatherProviders weathers) {
        this.device = device;
        this.weathers = weathers;
    }

    @Override
    public CommandResult handle(String command, String payloadJson) {
        log.info("{}: command '{}' {}", device.deviceId(), command, payloadJson == null ? "" : payloadJson);
        device.commandReceived(command);
        JsonElement payload;
        try {
            payload = Json.tree(payloadJson);
        } catch (IllegalArgumentException e) {
            return CommandResult.badRequest(e.getMessage());
        }
        return switch (command) {
            case WATER ->
                device.startWatering()
                        ? CommandResult.accepted("Started watering")
                        : new CommandResult(CommandResult.FAILED, Map.of("message", "Pump failure: no flow detected"));
            case REBOOT -> {
                device.startReboot();
                yield CommandResult.accepted("Started reboot");
            }
            case CALL_TECHNICIAN, REPAIR_SENSOR ->
                device.callTechnician()
                        ? CommandResult.accepted("Technician on the way")
                        : new CommandResult(CONFLICT, Map.of("message", "A job is already under way"));
            case REPOT ->
                device.repot()
                        ? CommandResult.accepted("Repotting")
                        : new CommandResult(CONFLICT, Map.of("message", "A job is already under way"));
            case SET_SPEED -> setSpeed(payload);
            case SET_WEAR -> setWear(payload);
            case PUMP -> pump(payload);
            case FAULT -> fault(payload);
            case SET_WEATHER -> setWeather(payload);
            case STATUS -> CommandResult.ok(device.state());
            default -> CommandResult.notFound(command);
        };
    }

    private CommandResult setSpeed(JsonElement payload) {
        JsonElement factor = field(payload, "factor");
        if (factor == null
                || !factor.isJsonPrimitive()
                || !factor.getAsJsonPrimitive().isNumber()) {
            return CommandResult.badRequest("Expected {\"factor\": <number>}");
        }
        double speed = factor.getAsDouble();
        if (speed < MIN_SPEED || speed > MAX_SPEED) {
            return CommandResult.badRequest("factor must be within " + MIN_SPEED + ".." + MAX_SPEED);
        }
        device.setSpeed(speed);
        return CommandResult.ok(device.state());
    }

    private CommandResult setWear(JsonElement payload) {
        JsonElement mean = field(payload, "meanTicks");
        if (mean == null
                || !mean.isJsonPrimitive()
                || !mean.getAsJsonPrimitive().isNumber()
                || mean.getAsLong() < 0) {
            return CommandResult.badRequest("Expected {\"meanTicks\": <readings between breakages, 0 for none>}");
        }
        device.setWear(mean.getAsLong());
        return CommandResult.ok(device.state());
    }

    private CommandResult pump(JsonElement payload) {
        JsonElement failed = field(payload, "failed");
        if (failed == null
                || !failed.isJsonPrimitive()
                || !failed.getAsJsonPrimitive().isBoolean()) {
            return CommandResult.badRequest("Expected {\"failed\": true|false}");
        }
        device.setPumpFailed(failed.getAsBoolean());
        return CommandResult.ok(device.state());
    }

    private CommandResult fault(JsonElement payload) {
        JsonElement type = field(payload, "type");
        if (type == null || !type.isJsonPrimitive()) {
            return CommandResult.badRequest(
                    "Expected {\"type\": one of " + Arrays.toString(SensorFault.values()) + "}");
        }
        try {
            device.setFault(SensorFault.valueOf(type.getAsString().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return CommandResult.badRequest("type must be one of " + Arrays.toString(SensorFault.values()));
        }
        return CommandResult.ok(device.state());
    }

    private CommandResult setWeather(JsonElement payload) {
        try {
            device.setWeather(weathers.create(WeatherProviders.Setting.fromJson(payload)));
        } catch (IllegalArgumentException e) {
            return CommandResult.badRequest(e.getMessage());
        }
        return CommandResult.ok(device.state());
    }

    private static JsonElement field(JsonElement payload, String name) {
        return payload instanceof JsonObject object ? object.get(name) : null;
    }
}
