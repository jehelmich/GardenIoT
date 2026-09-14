package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns raw hub messages into watering decisions.
 *
 * <p>Runs on the receive pipeline's thread, so it never throws: an unreadable message or a failed
 * command is logged and the stream carries on.
 */
public final class TelemetryProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessor.class);

    private final WateringPolicy policy;
    private final WateringActuator actuator;

    public TelemetryProcessor(WateringPolicy policy, WateringActuator actuator) {
        this.policy = policy;
        this.actuator = actuator;
    }

    public void onMessage(String body) {
        Telemetry telemetry;
        try {
            telemetry = TelemetryCodec.fromJson(body);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring message: {}", e.getMessage());
            return;
        }
        log.info("{}: temperature={}°C humidity={}%", telemetry.deviceId(),
                String.format("%.1f", telemetry.temperature()),
                String.format("%.1f", telemetry.humidity()));

        if (!policy.shouldWater(telemetry)) {
            return;
        }
        log.info("{}: soil is dry, requesting watering", telemetry.deviceId());
        try {
            actuator.water(telemetry.deviceId());
        } catch (ActuationException e) {
            log.warn("{}: {}", telemetry.deviceId(), e.getMessage());
        }
    }
}
