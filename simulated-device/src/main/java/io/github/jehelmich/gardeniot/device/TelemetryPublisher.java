package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * One iteration of the telemetry loop: take a reading, publish it.
 *
 * <p>Meant to be scheduled at a fixed delay. Failures are logged rather than thrown so that a
 * single dropped message does not silently cancel the schedule.
 */
final class TelemetryPublisher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);

    private final String deviceId;
    private final PlantSimulation plant;
    private final Clock clock;
    private final TelemetrySink sink;

    TelemetryPublisher(String deviceId, PlantSimulation plant, Clock clock, TelemetrySink sink) {
        this.deviceId = deviceId;
        this.plant = plant;
        this.clock = clock;
        this.sink = sink;
    }

    @Override
    public void run() {
        PlantSimulation.Reading reading = plant.next();
        Telemetry telemetry = new Telemetry(deviceId, clock.instant(), reading.temperature(), reading.humidity());
        try {
            sink.publish(telemetry);
            log.info("Sent temperature={}°C humidity={}%",
                    String.format("%.1f", telemetry.temperature()),
                    String.format("%.1f", telemetry.humidity()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Failed to send telemetry: {}", e.getMessage());
        }
    }
}
