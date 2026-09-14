package io.github.jehelmich.gardeniot.transport.mqtt;

import io.github.jehelmich.gardeniot.config.Environment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class MqttSettingsTest {

    @Test
    void defaultsToALocalUnauthenticatedBroker() {
        MqttSettings settings = MqttSettings.fromEnvironment(new Environment(Map.of()));

        assertThat(settings.host()).isEqualTo("localhost");
        assertThat(settings.port()).isEqualTo(1883);
        assertThat(settings.tls()).isFalse();
        assertThat(settings.username()).isEmpty();
        assertThat(settings.topicPrefix()).isEqualTo("garden");
        assertThat(settings.commandTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void readsEverySetting() {
        MqttSettings settings = MqttSettings.fromEnvironment(new Environment(Map.of(
                MqttSettings.HOST, "broker", MqttSettings.PORT, "8883", MqttSettings.TLS, "true",
                MqttSettings.USERNAME, "u", MqttSettings.PASSWORD, "p",
                MqttSettings.TOPIC_PREFIX, "greenhouse", MqttSettings.COMMAND_TIMEOUT, "3")));

        assertThat(settings.host()).isEqualTo("broker");
        assertThat(settings.port()).isEqualTo(8883);
        assertThat(settings.tls()).isTrue();
        assertThat(settings.username()).contains("u");
        assertThat(settings.password()).contains("p");
        assertThat(settings.topics().telemetry("x")).isEqualTo("greenhouse/x/telemetry");
        assertThat(settings.commandTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void rejectsNonsense() {
        assertThatIllegalStateException().isThrownBy(() -> MqttSettings.fromEnvironment(
                new Environment(Map.of(MqttSettings.PORT, "70000"))));
        assertThatIllegalStateException().isThrownBy(() -> MqttSettings.fromEnvironment(
                new Environment(Map.of(MqttSettings.TOPIC_PREFIX, "a/b"))));
        assertThatIllegalStateException().isThrownBy(() -> MqttSettings.fromEnvironment(
                new Environment(Map.of(MqttSettings.TOPIC_PREFIX, "+"))));
    }
}
