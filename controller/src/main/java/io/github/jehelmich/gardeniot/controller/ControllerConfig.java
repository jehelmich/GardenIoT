package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.config.Transport;
import java.time.Duration;

/**
 * Runtime settings for the controller; the transport reads its own on top of these.
 *
 * @param transport         how to reach the devices
 * @param humidityThreshold soil humidity in percent below which watering is triggered
 * @param silenceAfter      how long without a reading before a device is reported silent
 * @param wateringCooldown  minimum time between two watering commands to the same device; long
 *                          enough for the pump to run and the next reading to arrive, short
 *                          enough that a fast-running simulation is not left to die
 */
public record ControllerConfig(
        Transport transport, double humidityThreshold, Duration wateringCooldown, Duration silenceAfter) {

    public static final String HUMIDITY_THRESHOLD = "HUMIDITY_THRESHOLD";
    public static final String WATERING_COOLDOWN = "WATERING_COOLDOWN_SECONDS";
    public static final String SILENCE_AFTER = "SILENCE_AFTER_SECONDS";

    public ControllerConfig {
        if (humidityThreshold < 0.0 || humidityThreshold > 100.0) {
            throw new IllegalStateException(HUMIDITY_THRESHOLD + " must be within 0..100, was " + humidityThreshold);
        }
    }

    public static ControllerConfig fromEnvironment(Environment env) {
        return new ControllerConfig(
                Transport.fromEnvironment(env),
                env.optionalDouble(HUMIDITY_THRESHOLD, 25.0),
                env.optionalSeconds(WATERING_COOLDOWN, Duration.ofSeconds(15)),
                env.optionalSeconds(SILENCE_AFTER, Duration.ofSeconds(60)));
    }
}
