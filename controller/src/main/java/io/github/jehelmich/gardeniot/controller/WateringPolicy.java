package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decides whether a reading warrants watering.
 *
 * <p>A device is watered when its soil humidity drops below its threshold — the one its own
 * {@link WateringProfile} asks for if it reported one, the garden-wide default otherwise — but not more often
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

    /** Watering with the garden-wide threshold only. */
    public boolean shouldWater(Telemetry telemetry) {
        return shouldWater(telemetry, Optional.empty());
    }

    /**
     * @param profile what the device said it needs, if anything
     * @return {@code true} if the device should be watered now; the decision is recorded so that
     *         the cooldown applies to subsequent readings
     */
    public synchronized boolean shouldWater(Telemetry telemetry, Optional<WateringProfile> profile) {
        double threshold = profile.map(WateringProfile::minHumidity).orElse(humidityThreshold);
        if (telemetry.humidity() >= threshold) {
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
