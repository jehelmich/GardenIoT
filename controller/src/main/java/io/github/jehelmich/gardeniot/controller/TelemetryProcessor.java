package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import io.github.jehelmich.gardeniot.transport.CommandException;
import io.github.jehelmich.gardeniot.transport.DeviceProfileSource;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns readings into watering decisions.
 *
 * <p>Runs on the transport's receive thread, so it never throws: a failed command is logged and
 * the stream carries on.
 */
public final class TelemetryProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessor.class);

    private final WateringPolicy policy;
    private final WateringActuator actuator;
    private final DeviceProfileSource profiles;
    private final ControllerMetrics metrics;

    public TelemetryProcessor(WateringPolicy policy, WateringActuator actuator, ControllerMetrics metrics) {
        this(policy, actuator, DeviceProfileSource.NONE, metrics);
    }

    public TelemetryProcessor(
            WateringPolicy policy, WateringActuator actuator, DeviceProfileSource profiles, ControllerMetrics metrics) {
        this.policy = policy;
        this.actuator = actuator;
        this.profiles = profiles;
        this.metrics = metrics;
    }

    public void onTelemetry(Telemetry telemetry) {
        metrics.telemetryReceived(telemetry);
        log.info(
                "{}: temperature={}°C humidity={}%",
                telemetry.deviceId(),
                String.format("%.1f", telemetry.temperature()),
                String.format("%.1f", telemetry.humidity()));

        Optional<WateringProfile> profile = profiles.profileOf(telemetry.deviceId());
        if (!policy.shouldWater(telemetry, profile)) {
            return;
        }
        log.info(
                "{}: soil is dry for {}, requesting watering",
                telemetry.deviceId(),
                profile.map(WateringProfile::name).orElse("the default threshold"));
        try {
            actuator.water(telemetry.deviceId());
            metrics.wateringCommand(telemetry.deviceId(), "accepted");
        } catch (CommandException e) {
            metrics.wateringCommand(telemetry.deviceId(), "failed");
            log.warn("{}: {}", telemetry.deviceId(), e.getMessage());
        }
    }
}
