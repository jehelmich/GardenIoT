package io.github.jehelmich.gardeniot.controller;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import io.github.jehelmich.gardeniot.config.Environment;

import java.time.Duration;

/**
 * Runtime settings for the controller.
 *
 * @param eventHubConnectionString  connection string of the hub's built-in Event Hub-compatible endpoint
 * @param consumerGroup             consumer group to read telemetry from
 * @param iotHubConnectionString    service connection string used to invoke direct methods
 * @param humidityThreshold         soil humidity in percent below which watering is triggered
 * @param wateringCooldown          minimum time between two watering commands to the same device
 * @param methodResponseTimeout     how long to wait for the device to answer a direct method
 * @param methodConnectTimeout      how long to wait for the device to be reachable
 */
public record ControllerConfig(String eventHubConnectionString,
                               String consumerGroup,
                               String iotHubConnectionString,
                               double humidityThreshold,
                               Duration wateringCooldown,
                               Duration methodResponseTimeout,
                               Duration methodConnectTimeout) {

    public static final String EVENTHUB_CONNECTION_STRING = "EVENTHUB_COMPATIBLE_CONNECTION_STRING";
    public static final String EVENTHUB_CONSUMER_GROUP = "EVENTHUB_CONSUMER_GROUP";
    public static final String IOTHUB_CONNECTION_STRING = "IOTHUB_SERVICE_CONNECTION_STRING";
    public static final String HUMIDITY_THRESHOLD = "HUMIDITY_THRESHOLD";
    public static final String WATERING_COOLDOWN = "WATERING_COOLDOWN_SECONDS";

    public ControllerConfig {
        if (humidityThreshold < 0.0 || humidityThreshold > 100.0) {
            throw new IllegalStateException(HUMIDITY_THRESHOLD + " must be within 0..100, was " + humidityThreshold);
        }
    }

    public static ControllerConfig fromEnvironment(Environment env) {
        return new ControllerConfig(
                env.required(EVENTHUB_CONNECTION_STRING),
                env.optional(EVENTHUB_CONSUMER_GROUP, EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME),
                env.required(IOTHUB_CONNECTION_STRING),
                env.optionalDouble(HUMIDITY_THRESHOLD, 25.0),
                env.optionalSeconds(WATERING_COOLDOWN, Duration.ofSeconds(60)),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5));
    }
}
