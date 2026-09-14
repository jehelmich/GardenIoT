package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.config.Environment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ControllerConfigTest {

    private static final Map<String, String> REQUIRED = Map.of(
            ControllerConfig.EVENTHUB_CONNECTION_STRING, "Endpoint=sb://h/;EntityPath=hub",
            ControllerConfig.IOTHUB_CONNECTION_STRING, "HostName=h;SharedAccessKeyName=service;SharedAccessKey=k");

    @Test
    void appliesDefaultsForOptionalSettings() {
        ControllerConfig config = ControllerConfig.fromEnvironment(new Environment(REQUIRED));

        assertThat(config.consumerGroup()).isEqualTo("$Default");
        assertThat(config.humidityThreshold()).isEqualTo(25.0);
        assertThat(config.wateringCooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.methodResponseTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.methodConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void readsOptionalSettings() {
        Map<String, String> env = new java.util.HashMap<>(REQUIRED);
        env.put(ControllerConfig.EVENTHUB_CONSUMER_GROUP, "controller");
        env.put(ControllerConfig.HUMIDITY_THRESHOLD, "40");
        env.put(ControllerConfig.WATERING_COOLDOWN, "300");

        ControllerConfig config = ControllerConfig.fromEnvironment(new Environment(env));

        assertThat(config.consumerGroup()).isEqualTo("controller");
        assertThat(config.humidityThreshold()).isEqualTo(40.0);
        assertThat(config.wateringCooldown()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void requiresBothConnectionStrings() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ControllerConfig.fromEnvironment(new Environment(Map.of())))
                .withMessageContaining(ControllerConfig.EVENTHUB_CONNECTION_STRING);
        assertThatIllegalStateException()
                .isThrownBy(() -> ControllerConfig.fromEnvironment(new Environment(
                        Map.of(ControllerConfig.EVENTHUB_CONNECTION_STRING, "x"))))
                .withMessageContaining(ControllerConfig.IOTHUB_CONNECTION_STRING);
    }

    @Test
    void rejectsAThresholdOutsideThePercentRange() {
        Map<String, String> env = new java.util.HashMap<>(REQUIRED);
        env.put(ControllerConfig.HUMIDITY_THRESHOLD, "150");

        assertThatIllegalStateException()
                .isThrownBy(() -> ControllerConfig.fromEnvironment(new Environment(env)))
                .withMessageContaining("0..100");
    }
}
