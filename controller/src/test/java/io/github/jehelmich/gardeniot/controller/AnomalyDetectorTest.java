package io.github.jehelmich.gardeniot.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.transport.AlertPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

    private static final Instant T0 = Instant.parse("2017-07-17T10:15:30Z");

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        Instant now = T0;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final List<String> published = new ArrayList<>();
    private final AlertPublisher publisher = new AlertPublisher() {
        @Override
        public void raise(String deviceId, String alert, String message) {
            published.add("raise " + deviceId + " " + alert);
        }

        @Override
        public void clear(String deviceId, String alert) {
            published.add("clear " + deviceId + " " + alert);
        }
    };
    private final ManualClock clock = new ManualClock();
    private final Metrics metrics = new Metrics();
    private final AnomalyDetector detector =
            new AnomalyDetector(publisher, new ControllerMetrics(metrics), clock, Duration.ofSeconds(60));

    private static Telemetry reading(String deviceId, double humidity) {
        return new Telemetry(deviceId, T0, 22.0, humidity);
    }

    @Test
    void aSensorThatNeverChangesLooksStuck() {
        IntStream.range(0, AnomalyDetector.STUCK_AFTER_READINGS - 1)
                .forEach(i -> detector.onTelemetry(reading("basil", 40.0)));
        assertThat(published).isEmpty();

        detector.onTelemetry(reading("basil", 40.0));

        assertThat(published).containsExactly("raise basil sensorStuck");
        assertThat(detector.activeAlerts("basil")).containsExactly("sensorStuck");
        assertThat(metrics.scrape()).contains("gardeniot_controller_alert_sensorstuck{device=\"basil\"} 1.0");

        detector.onTelemetry(reading("basil", 40.0));
        assertThat(published).as("raised once").hasSize(1);

        detector.onTelemetry(reading("basil", 39.7));
        assertThat(published).containsExactly("raise basil sensorStuck", "clear basil sensorStuck");
        assertThat(detector.activeAlerts("basil")).isEmpty();
    }

    @Test
    void aSaturatedSensorIsNotStuck() {
        IntStream.range(0, 20).forEach(i -> detector.onTelemetry(reading("pond", 100.0)));
        IntStream.range(0, 20).forEach(i -> detector.onTelemetry(reading("desert", 3.0)));

        assertThat(published).isEmpty();
    }

    @Test
    void aRejectedWateringIsAPumpFaultUntilOneIsAccepted() {
        detector.onWateringRejected("basil", "Pump failure: no flow detected");
        detector.onWateringRejected("basil", "Pump failure: no flow detected");
        assertThat(published).containsExactly("raise basil pumpFault");

        detector.onWateringAccepted(reading("basil", 20.0));
        assertThat(published).containsExactly("raise basil pumpFault", "clear basil pumpFault");
    }

    @Test
    void realSoilVariesSoItIsNotStuck() {
        IntStream.range(0, 20).forEach(i -> detector.onTelemetry(reading("basil", 40.0 - i * 0.2)));

        assertThat(published).isEmpty();
    }

    @Test
    void wateringThatDoesNotWetTheSoilIsFlagged() {
        detector.onTelemetry(reading("basil", 20.0));
        detector.onWateringAccepted(reading("basil", 20.0));
        IntStream.range(0, AnomalyDetector.WATERING_JUDGED_AFTER_READINGS - 1)
                .forEach(i -> detector.onTelemetry(reading("basil", 19.0 - i * 0.3)));
        assertThat(published).isEmpty();

        detector.onTelemetry(reading("basil", 17.0));

        assertThat(published).containsExactly("raise basil wateringIneffective");

        detector.onWateringAccepted(reading("basil", 17.0));
        detector.onTelemetry(reading("basil", 60.0));
        assertThat(published).containsExactly("raise basil wateringIneffective", "clear basil wateringIneffective");
    }

    @Test
    void wateringThatWorksIsNotFlagged() {
        detector.onTelemetry(reading("basil", 20.0));
        detector.onWateringAccepted(reading("basil", 20.0));
        detector.onTelemetry(reading("basil", 21.0));
        detector.onTelemetry(reading("basil", 64.0));
        IntStream.range(0, 10).forEach(i -> detector.onTelemetry(reading("basil", 63.0 - i)));

        assertThat(published).isEmpty();
    }

    @Test
    void aDeviceThatStopsTalkingIsReportedSilentUntilItReturns() {
        detector.onTelemetry(reading("basil", 40.0));
        clock.now = T0.plusSeconds(30);
        detector.checkSilence();
        assertThat(published).isEmpty();

        clock.now = T0.plusSeconds(61);
        detector.checkSilence();
        detector.checkSilence();
        assertThat(published).containsExactly("raise basil silent");

        detector.onTelemetry(reading("basil", 39.0));
        assertThat(published).containsExactly("raise basil silent", "clear basil silent");
    }
}
