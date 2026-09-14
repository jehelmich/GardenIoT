package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;

/** What the controller exposes to Prometheus. */
public final class ControllerMetrics {

    static final String TELEMETRY_TOTAL = "gardeniot_controller_telemetry_total";
    static final String HUMIDITY = "gardeniot_controller_humidity_percent";
    static final String TEMPERATURE = "gardeniot_controller_temperature_celsius";
    static final String WATERING_COMMANDS_TOTAL = "gardeniot_controller_watering_commands_total";

    private final Metrics metrics;

    public ControllerMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    void telemetryReceived(Telemetry telemetry) {
        metrics.counter(TELEMETRY_TOTAL, "Readings received", Metrics.DEVICE_TAG, telemetry.deviceId())
                .increment();
        metrics.gauge(
                HUMIDITY, "Soil humidity as last reported by the device", telemetry.deviceId(), telemetry.humidity());
        metrics.gauge(
                TEMPERATURE,
                "Air temperature as last reported by the device",
                telemetry.deviceId(),
                telemetry.temperature());
    }

    void wateringCommand(String deviceId, String outcome) {
        metrics.counter(
                        WATERING_COMMANDS_TOTAL,
                        "Watering commands by outcome",
                        Metrics.DEVICE_TAG,
                        deviceId,
                        "outcome",
                        outcome)
                .increment();
    }
}
