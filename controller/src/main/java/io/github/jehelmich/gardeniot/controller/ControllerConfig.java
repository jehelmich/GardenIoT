package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.config.Transport;

import java.time.Duration;

/**
 * Runtime settings for the controller; the transport reads its own on top of these.
 *
 * @param transport         how to reach the devices
 * @param humidityThreshold soil humidity in percent below which watering is triggered
 * @param wateringCooldown  minimum time between two watering commands to the same device
 */
public record ControllerConfig(Transport transport, double humidityThreshold, Duration wateringCooldown) {

    public static final String HUMIDITY_THRESHOLD = "HUMIDITY_THRESHOLD";
    public static final String WATERING_COOLDOWN = "WATERING_COOLDOWN_SECONDS";

    public ControllerConfig {
        if (humidityThreshold < 0.0 || humidityThreshold > 100.0) {
            throw new IllegalStateException(HUMIDITY_THRESHOLD + " must be within 0..100, was " + humidityThreshold);
        }
    }

    public static ControllerConfig fromEnvironment(Environment env) {
        return new ControllerConfig(
                Transport.fromEnvironment(env),
                env.optionalDouble(HUMIDITY_THRESHOLD, 25.0),
                env.optionalSeconds(WATERING_COOLDOWN, Duration.ofSeconds(60)));
    }
}
