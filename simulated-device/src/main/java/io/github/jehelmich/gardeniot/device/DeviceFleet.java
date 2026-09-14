package io.github.jehelmich.gardeniot.device;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.DeviceTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The plants hosted by this process, and the fleet commands that add and remove them:
 *
 * <pre>
 * addPlant    {"deviceId": "basil"}   start a new plant in this process; 200 / 409
 * removePlant {"deviceId": "basil"}   stop it; 200 / 404
 * listPlants                          the ids hosted here; 200
 * </pre>
 */
public final class DeviceFleet implements CommandHandler, AutoCloseable {

    static final String ADD_PLANT = "addPlant";
    static final String REMOVE_PLANT = "removePlant";
    static final String LIST_PLANTS = "listPlants";

    private static final Logger log = LoggerFactory.getLogger(DeviceFleet.class);
    private static final double MIN_TEMPERATURE = 15.0;
    private static final double MIN_HUMIDITY = 15.0;

    private final DeviceConfig config;
    private final DeviceTransportFactory transports;
    private final Clock clock;
    private final DeviceMetrics metrics;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService actions = Executors.newCachedThreadPool();
    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    public DeviceFleet(DeviceConfig config, DeviceTransportFactory transports, Clock clock, DeviceMetrics metrics) {
        this.config = config;
        this.transports = transports;
        this.clock = clock;
        this.metrics = metrics;
    }

    public VirtualDevice add(String deviceId) throws Exception {
        DeviceConfig.requireValidId(deviceId);
        VirtualDevice device = new VirtualDevice(deviceId,
                new PlantSimulation(MIN_TEMPERATURE, MIN_HUMIDITY, new Random()),
                config.telemetryInterval(), config.actionDuration(), clock, scheduler, actions, metrics);
        if (devices.putIfAbsent(deviceId, device) != null) {
            throw new IllegalStateException("Device '" + deviceId + "' is already hosted here");
        }
        try {
            device.start(transports);
        } catch (Exception e) {
            devices.remove(deviceId);
            throw e;
        }
        return device;
    }

    public boolean remove(String deviceId) {
        VirtualDevice device = devices.remove(deviceId);
        if (device == null) {
            return false;
        }
        device.close();
        metrics.forget(deviceId);
        return true;
    }

    public List<String> deviceIds() {
        return devices.keySet().stream().sorted().toList();
    }

    @Override
    public CommandResult handle(String command, String payloadJson) {
        log.info("fleet command '{}' {}", command, payloadJson == null ? "" : payloadJson);
        return switch (command) {
            case ADD_PLANT -> {
                String id = deviceIdIn(payloadJson);
                if (id == null) {
                    yield CommandResult.badRequest("Expected {\"deviceId\": \"...\"}");
                }
                try {
                    add(id);
                    yield CommandResult.ok(Map.of("deviceId", id, "hostedBy", hostName()));
                } catch (IllegalArgumentException e) {
                    yield CommandResult.badRequest(e.getMessage());
                } catch (IllegalStateException e) {
                    yield new CommandResult(409, Map.of("message", e.getMessage()));
                } catch (Exception e) {
                    yield new CommandResult(CommandResult.FAILED, Map.of("message", e.toString()));
                }
            }
            case REMOVE_PLANT -> {
                String id = deviceIdIn(payloadJson);
                if (id == null) {
                    yield CommandResult.badRequest("Expected {\"deviceId\": \"...\"}");
                }
                yield remove(id)
                        ? CommandResult.ok(Map.of("deviceId", id))
                        : new CommandResult(CommandResult.NOT_FOUND, Map.of("message", "Not hosted here: " + id));
            }
            case LIST_PLANTS -> CommandResult.ok(Map.of("hostedBy", hostName(), "deviceIds", deviceIds()));
            default -> CommandResult.notFound(command);
        };
    }

    private static String deviceIdIn(String payloadJson) {
        try {
            JsonElement payload = Json.tree(payloadJson);
            if (payload instanceof JsonObject object && object.get("deviceId") != null
                    && object.get("deviceId").isJsonPrimitive()) {
                return object.get("deviceId").getAsString();
            }
        } catch (IllegalArgumentException e) {
            // fall through
        }
        return null;
    }

    static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return "unknown";
        }
    }

    @Override
    public void close() {
        devices.keySet().forEach(this::remove);
        scheduler.shutdownNow();
        actions.shutdownNow();
    }
}
