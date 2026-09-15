package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.transport.AlertPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notices what a device cannot notice about itself, from the outside:
 *
 * <ul>
 *   <li>{@value #SENSOR_STUCK}: the same humidity, to the digit, for many readings in a row —
 *       real soil never does that;
 *   <li>{@value #WATERING_INEFFECTIVE}: a watering command was accepted, yet the next readings
 *       show no more water in the soil — a dead pump, or a sensor that is lying;
 *   <li>{@value #SILENT}: no reading for a long while;
 *   <li>{@value #PUMP_FAULT}: the device itself rejected a watering command — its flow meter saw
 *       nothing come out. Not an inference, but the same alert channel.
 * </ul>
 *
 * Alerts are raised once, cleared when the symptom goes away, and published so that operators
 * (and the garden page) can act — by sending someone to look.
 */
public final class AnomalyDetector {

    public static final String SENSOR_STUCK = "sensorStuck";
    public static final String WATERING_INEFFECTIVE = "wateringIneffective";
    public static final String SILENT = "silent";
    public static final String PUMP_FAULT = "pumpFault";

    static final int STUCK_AFTER_READINGS = 8;
    /**
     * A sensor pinned at its range limits is saturated, not necessarily stuck: waterlogged soil
     * really does read 100, and a probe in dust bottoms out at a few percent.
     */
    static final double SATURATION_LOW = 5.0;

    static final double SATURATION_HIGH = 99.5;
    static final int WATERING_JUDGED_AFTER_READINGS = 6;
    static final double EXPECTED_RISE = 10.0;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnomalyDetector.class);

    private static final class DeviceState {
        final Deque<Double> recent = new ArrayDeque<>();
        final Set<String> active = new HashSet<>();
        Instant lastSeen;
        Double humidityWhenWatered;
        int readingsSinceWatered = -1;
    }

    private final AlertPublisher publisher;
    private final ControllerMetrics metrics;
    private final Clock clock;
    private final Duration silenceAfter;
    private final Map<String, DeviceState> devices = new HashMap<>();

    public AnomalyDetector(AlertPublisher publisher, ControllerMetrics metrics, Clock clock, Duration silenceAfter) {
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
        this.silenceAfter = silenceAfter;
    }

    public synchronized void onTelemetry(Telemetry telemetry) {
        DeviceState state = devices.computeIfAbsent(telemetry.deviceId(), id -> new DeviceState());
        state.lastSeen = clock.instant();
        clear(telemetry.deviceId(), state, SILENT);

        state.recent.addLast(telemetry.humidity());
        while (state.recent.size() > STUCK_AFTER_READINGS) {
            state.recent.removeFirst();
        }
        boolean stuck = state.recent.size() == STUCK_AFTER_READINGS
                && state.recent.stream().distinct().count() == 1
                && telemetry.humidity() > SATURATION_LOW
                && telemetry.humidity() < SATURATION_HIGH;
        if (stuck) {
            raise(
                    telemetry.deviceId(),
                    state,
                    SENSOR_STUCK,
                    "Humidity has read exactly " + telemetry.humidity() + "% for " + STUCK_AFTER_READINGS
                            + " readings");
        } else {
            clear(telemetry.deviceId(), state, SENSOR_STUCK);
        }

        if (state.readingsSinceWatered >= 0) {
            state.readingsSinceWatered++;
            if (telemetry.humidity() >= state.humidityWhenWatered + EXPECTED_RISE) {
                state.readingsSinceWatered = -1;
                clear(telemetry.deviceId(), state, WATERING_INEFFECTIVE);
            } else if (state.readingsSinceWatered >= WATERING_JUDGED_AFTER_READINGS) {
                state.readingsSinceWatered = -1;
                raise(
                        telemetry.deviceId(),
                        state,
                        WATERING_INEFFECTIVE,
                        "Watering was accepted but humidity did not rise from "
                                + String.format(java.util.Locale.ROOT, "%.1f", state.humidityWhenWatered) + "%");
            }
        }
    }

    /** The controller sent a watering command and the device accepted it. */
    public synchronized void onWateringAccepted(Telemetry trigger) {
        DeviceState state = devices.computeIfAbsent(trigger.deviceId(), id -> new DeviceState());
        state.humidityWhenWatered = trigger.humidity();
        state.readingsSinceWatered = 0;
        clear(trigger.deviceId(), state, PUMP_FAULT);
    }

    /** The device turned a watering command down. */
    public synchronized void onWateringRejected(String deviceId, String reason) {
        DeviceState state = devices.computeIfAbsent(deviceId, id -> new DeviceState());
        raise(deviceId, state, PUMP_FAULT, reason);
    }

    /** Call periodically: raises {@value #SILENT} for devices not heard from lately. */
    public synchronized void checkSilence() {
        Instant now = clock.instant();
        devices.forEach((deviceId, state) -> {
            if (state.lastSeen != null && Duration.between(state.lastSeen, now).compareTo(silenceAfter) > 0) {
                raise(
                        deviceId,
                        state,
                        SILENT,
                        "No reading for "
                                + Duration.between(state.lastSeen, now).toSeconds() + "s");
            }
        });
    }

    public synchronized List<String> activeAlerts(String deviceId) {
        DeviceState state = devices.get(deviceId);
        return state == null ? List.of() : state.active.stream().sorted().toList();
    }

    private void raise(String deviceId, DeviceState state, String alert, String message) {
        if (state.active.add(alert)) {
            log.warn("{}: ALERT {} — {}", deviceId, alert, message);
            metrics.alert(deviceId, alert, true);
            publisher.raise(deviceId, alert, message);
        }
    }

    private void clear(String deviceId, DeviceState state, String alert) {
        if (state.active.remove(alert)) {
            log.info("{}: alert {} cleared", deviceId, alert);
            metrics.alert(deviceId, alert, false);
            publisher.clear(deviceId, alert);
        }
    }
}
