package io.github.jehelmich.gardeniot.transport.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver.BusMessage;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver.Kind;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MqttBusObserverTest {

    private final MqttBusObserver observer = new MqttBusObserver(MqttSettings.local("localhost", 1883));

    private static Mqtt5Publish publish(String topic, String payload) {
        return Mqtt5Publish.builder()
                .topic(topic)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void subscribesToEveryTopicFamilyItClassifies() {
        assertThat(observer.subscriptions())
                .containsExactlyInAnyOrder(
                        "garden/+/telemetry",
                        "garden/+/state/+",
                        "garden/+/status",
                        "garden/+/alert/+",
                        "garden/+/cmd/+",
                        "garden/_fleet/cmd/+");
    }

    @Test
    void classifiesEveryTopicFamily() {
        assertThat(observer.classify(publish("garden/basil/telemetry", "{}")))
                .isEqualTo(new BusMessage(Kind.TELEMETRY, "basil", null, "{}"));
        assertThat(observer.classify(publish("garden/basil/state/simulation", "{}")))
                .isEqualTo(new BusMessage(Kind.STATE, "basil", "simulation", "{}"));
        assertThat(observer.classify(publish("garden/basil/status", "online")))
                .isEqualTo(new BusMessage(Kind.STATUS, "basil", null, "online"));
        assertThat(observer.classify(publish("garden/basil/alert/sensorStuck", "{}")))
                .isEqualTo(new BusMessage(Kind.ALERT, "basil", "sensorStuck", "{}"));
        assertThat(observer.classify(publish("garden/basil/cmd/water", "")))
                .isEqualTo(new BusMessage(Kind.COMMAND, "basil", "water", ""));
        assertThat(observer.classify(publish("garden/_fleet/cmd/addPlant", "{}")))
                .isEqualTo(new BusMessage(Kind.FLEET_COMMAND, null, "addPlant", "{}"));
        assertThat(observer.classify(publish("garden/_reply/x", "{}"))).isNull();
        assertThat(observer.classify(publish("other/basil/telemetry", "{}"))).isNull();
    }
}
