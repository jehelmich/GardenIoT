package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.AlertPublisher;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Alerts as retained messages on {@code {prefix}/{deviceId}/alert/{alert}}; clearing empties the topic. */
public final class MqttAlertPublisher implements AlertPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MqttAlertPublisher.class);

    private final MqttSettings settings;
    private final MqttTopics topics;
    private final Clock clock;
    private Mqtt5AsyncClient client;

    public MqttAlertPublisher(MqttSettings settings, Clock clock) {
        this.settings = settings;
        this.topics = settings.topics();
        this.clock = clock;
    }

    public MqttAlertPublisher start() throws Exception {
        client = MqttClients.connect(settings, "alerts-" + UUID.randomUUID(), null, null);
        return this;
    }

    @Override
    public void raise(String deviceId, String alert, String message) {
        publish(
                topics.alert(deviceId, alert),
                Json.stringify(
                        Map.of("message", message, "raisedAt", clock.instant().toString())));
    }

    @Override
    public void clear(String deviceId, String alert) {
        publish(topics.alert(deviceId, alert), "");
    }

    private void publish(String topic, String payload) {
        if (client == null) {
            throw new IllegalStateException("start() first");
        }
        client.publishWith()
                .topic(topic)
                .qos(MqttClients.QOS)
                .retain(true)
                .payload(MqttClients.utf8(payload))
                .send()
                .whenComplete((ack, error) -> {
                    if (error != null) {
                        log.warn("Could not publish {}: {}", topic, error.getMessage());
                    }
                });
    }

    @Override
    public void close() {
        if (client != null) {
            client.disconnect();
        }
    }
}
