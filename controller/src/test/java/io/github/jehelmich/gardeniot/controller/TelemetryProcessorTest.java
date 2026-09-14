package io.github.jehelmich.gardeniot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.transport.CommandException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryProcessorTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    private final List<String> watered = new ArrayList<>();
    private final WateringPolicy policy =
            new WateringPolicy(25.0, Duration.ofMinutes(1), Clock.fixed(NOW, ZoneOffset.UTC));
    private final Metrics metrics = new Metrics();
    private final TelemetryProcessor processor =
            new TelemetryProcessor(policy, watered::add, new ControllerMetrics(metrics));

    private static Telemetry reading(String deviceId, double humidity) {
        return new Telemetry(deviceId, NOW, 22.0, humidity);
    }

    @Test
    void watersTheDeviceThatReportedDrySoil() {
        processor.onTelemetry(reading("garden-1", 10.0));

        assertThat(watered).containsExactly("garden-1");
    }

    @Test
    void leavesWetSoilAlone() {
        processor.onTelemetry(reading("garden-1", 60.0));

        assertThat(watered).isEmpty();
    }

    @Test
    void aFailedCommandDoesNotStopTheStream() {
        TelemetryProcessor failing = new TelemetryProcessor(
                policy,
                deviceId -> {
                    throw new CommandException("device offline");
                },
                new ControllerMetrics(metrics));

        assertThatNoException().isThrownBy(() -> failing.onTelemetry(reading("garden-1", 10.0)));
        assertThat(metrics.scrape()).contains("outcome=\"failed\"");
    }

    @Test
    void publishesWhatItSawAndDid() {
        processor.onTelemetry(reading("garden-1", 10.0));

        assertThat(metrics.scrape())
                .contains("gardeniot_controller_humidity_percent{device=\"garden-1\"} 10.0")
                .contains("gardeniot_controller_watering_commands_total{device=\"garden-1\",outcome=\"accepted\"} 1.0");
    }
}
