package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.transport.CommandException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TelemetryProcessorTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    private final List<String> watered = new ArrayList<>();
    private final WateringPolicy policy = new WateringPolicy(25.0, Duration.ofMinutes(1), Clock.fixed(NOW, ZoneOffset.UTC));
    private final TelemetryProcessor processor = new TelemetryProcessor(policy, watered::add);

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
        TelemetryProcessor failing = new TelemetryProcessor(policy, deviceId -> {
            throw new CommandException("device offline");
        });

        assertThatNoException().isThrownBy(() -> failing.onTelemetry(reading("garden-1", 10.0)));
    }
}
