package io.github.jehelmich.gardeniot.telemetry;

import java.time.Instant;
import java.util.Objects;

/**
 * One sensor reading as published by a device and consumed by the controller.
 *
 * <p>This record is the wire contract between the two halves of the system; see
 * {@link TelemetryCodec} for the JSON shape.
 *
 * @param deviceId    the IoT Hub device identity that produced the reading
 * @param timestamp   when the reading was taken, on the device's clock
 * @param temperature air temperature in degrees Celsius
 * @param humidity    soil humidity in percent, 0 to 100
 */
public record Telemetry(String deviceId, Instant timestamp, double temperature, double humidity) {

    public Telemetry {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(timestamp, "timestamp");
        if (humidity < 0.0 || humidity > 100.0) {
            throw new IllegalArgumentException("humidity must be within 0..100, was " + humidity);
        }
    }
}
