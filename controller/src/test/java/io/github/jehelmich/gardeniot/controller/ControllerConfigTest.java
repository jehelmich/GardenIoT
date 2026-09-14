package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.config.Transport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ControllerConfigTest {

    @Test
    void defaultsToMqttWithSensibleTunables() {
        ControllerConfig config = ControllerConfig.fromEnvironment(new Environment(Map.of()));

        assertThat(config.transport()).isEqualTo(Transport.MQTT);
        assertThat(config.humidityThreshold()).isEqualTo(25.0);
        assertThat(config.wateringCooldown()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void readsTheSettings() {
        ControllerConfig config = ControllerConfig.fromEnvironment(new Environment(Map.of(
                Transport.VARIABLE, "azure",
                ControllerConfig.HUMIDITY_THRESHOLD, "40",
                ControllerConfig.WATERING_COOLDOWN, "300")));

        assertThat(config.transport()).isEqualTo(Transport.AZURE);
        assertThat(config.humidityThreshold()).isEqualTo(40.0);
        assertThat(config.wateringCooldown()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsAThresholdOutsideThePercentRange() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ControllerConfig.fromEnvironment(
                        new Environment(Map.of(ControllerConfig.HUMIDITY_THRESHOLD, "150"))))
                .withMessageContaining("0..100");
    }

    @Test
    void rejectsAnUnknownTransport() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ControllerConfig.fromEnvironment(new Environment(Map.of(Transport.VARIABLE, "carrier-pigeon"))))
                .withMessageContaining("TRANSPORT");
    }
}
