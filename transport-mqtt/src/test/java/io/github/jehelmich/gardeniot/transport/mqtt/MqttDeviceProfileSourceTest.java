package io.github.jehelmich.gardeniot.transport.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MqttDeviceProfileSourceTest {

    private final MqttDeviceProfileSource source = new MqttDeviceProfileSource(MqttSettings.local("localhost", 1883));

    private static Mqtt5Publish publish(String topic, String payload) {
        return Mqtt5Publish.builder()
                .topic(topic)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void remembersProfilesPerDeviceAndForgetsClearedOnes() {
        source.onProfile(
                publish("garden/fern/state/profile", "{\"name\":\"Fern\",\"minHumidity\":55,\"maxHumidity\":90}"));
        source.onProfile(publish("garden/odd/state/profile", "{\"name\":\"x\",\"minHumidity\":90,\"maxHumidity\":10}"));

        assertThat(source.profileOf("fern")).contains(new WateringProfile("Fern", 55, 90));
        assertThat(source.profileOf("odd")).isEmpty();
        assertThat(source.profileOf("nobody")).isEmpty();

        source.onProfile(publish("garden/fern/state/profile", ""));
        assertThat(source.profileOf("fern")).isEmpty();
    }
}
