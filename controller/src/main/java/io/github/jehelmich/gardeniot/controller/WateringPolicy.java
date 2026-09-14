package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether a reading warrants watering.
 *
 * <p>A device is watered when its soil humidity drops below the threshold, but not more often
 * than once per cooldown: the pump takes a while and the next few readings still reflect the
 * dry soil, so without the cooldown every reading in that window would trigger another command.
 */
public final class WateringPolicy {

    private final double humidityThreshold;
    private final Duration cooldown;
    private final Clock clock;
    private final Map<String, Instant> lastWatered = new HashMap<>();

    public WateringPolicy(double humidityThreshold, Duration cooldown, Clock clock) {
        this.humidityThreshold = humidityThreshold;
        this.cooldown = cooldown;
        this.clock = clock;
    }

    /**
     * @return {@code true} if the device should be watered now; the decision is recorded so that
     *         the cooldown applies to subsequent readings
     */
    public synchronized boolean shouldWater(Telemetry telemetry) {
        if (telemetry.humidity() >= humidityThreshold) {
            return false;
        }
        Instant now = clock.instant();
        Instant last = lastWatered.get(telemetry.deviceId());
        if (last != null && Duration.between(last, now).compareTo(cooldown) < 0) {
            return false;
        }
        lastWatered.put(telemetry.deviceId(), now);
        return true;
    }
}
