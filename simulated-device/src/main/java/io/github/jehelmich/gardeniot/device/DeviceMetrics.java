package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.device.PlantSimulation.Reading;
import io.github.jehelmich.gardeniot.observability.Metrics;

/**
 * What a device process exposes to Prometheus — including the simulator's true values, which a
 * real device could not know but which make a sensor fault visible on a dashboard.
 */
public final class DeviceMetrics {

    static final String TRUE_HUMIDITY = "gardeniot_device_true_humidity_percent";
    static final String REPORTED_HUMIDITY = "gardeniot_device_reported_humidity_percent";
    static final String TEMPERATURE = "gardeniot_device_temperature_celsius";
    static final String SPEED = "gardeniot_device_speed_factor";
    static final String FAULT = "gardeniot_device_sensor_fault_code";
    static final String TELEMETRY_SENT_TOTAL = "gardeniot_device_telemetry_sent_total";
    static final String WATERINGS_TOTAL = "gardeniot_device_waterings_total";
    static final String COMMANDS_TOTAL = "gardeniot_device_commands_total";

    private final Metrics metrics;

    public DeviceMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    void tick(String deviceId, Reading truth, Reading reported, SensorFault fault, double speed) {
        metrics.gauge(TRUE_HUMIDITY, "Actual soil humidity in the simulation", deviceId, truth.humidity());
        metrics.gauge(TEMPERATURE, "Air temperature in the simulation", deviceId, truth.temperature());
        metrics.gauge(SPEED, "Simulation speed factor", deviceId, speed);
        metrics.gauge(FAULT, "Sensor fault: 0 none, 1 stuck, 2 over-reading, 3 silent", deviceId, fault.ordinal());
        if (reported != null) {
            metrics.gauge(REPORTED_HUMIDITY, "Soil humidity as the sensor reported it", deviceId, reported.humidity());
            metrics.counter(TELEMETRY_SENT_TOTAL, "Readings published", Metrics.DEVICE_TAG, deviceId)
                    .increment();
        }
    }

    void watered(String deviceId) {
        metrics.counter(WATERINGS_TOTAL, "Times the pump ran", Metrics.DEVICE_TAG, deviceId)
                .increment();
    }

    void command(String deviceId, String command) {
        metrics.counter(COMMANDS_TOTAL, "Commands received", Metrics.DEVICE_TAG, deviceId, "command", command)
                .increment();
    }

    void forget(String deviceId) {
        metrics.forget(deviceId);
    }
}
