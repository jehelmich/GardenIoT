package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import io.github.jehelmich.gardeniot.transport.TelemetrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reads every device's telemetry topic.
 *
 * <p>As with IoT Hub's system property, the device identity comes from the topic the broker
 * delivered the message on rather than from the body.
 */
public final class MqttTelemetrySource implements TelemetrySource {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySource.class);

    private final MqttSettings settings;
    private final MqttTopics topics;
    private Mqtt5AsyncClient client;

    public MqttTelemetrySource(MqttSettings settings) {
        this.settings = settings;
        this.topics = settings.topics();
    }

    @Override
    public void start(Consumer<Telemetry> onTelemetry, Consumer<Throwable> onFailure) throws Exception {
        client = MqttClients.connect(settings, "telemetry-" + UUID.randomUUID(), null, null);
        client.subscribeWith()
                .topicFilter(topics.allTelemetry())
                .qos(MqttClients.QOS)
                .callback(publish -> decode(publish).ifPresent(onTelemetry))
                .send()
                .get(30, TimeUnit.SECONDS);
        log.info("Subscribed to {}", topics.allTelemetry());
    }

    Optional<Telemetry> decode(Mqtt5Publish publish) {
        String topicDeviceId = topics.deviceIdOf(publish.getTopic().toString());
        try {
            Telemetry telemetry = TelemetryCodec.fromJson(MqttClients.utf8(publish.getPayloadAsBytes()));
            if (topicDeviceId != null && !topicDeviceId.equals(telemetry.deviceId())) {
                log.warn("Message on '{}' claims to be from '{}'; trusting the topic",
                        publish.getTopic(), telemetry.deviceId());
                telemetry = new Telemetry(topicDeviceId, telemetry.timestamp(),
                        telemetry.temperature(), telemetry.humidity());
            }
            return Optional.of(telemetry);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring message on '{}': {}", publish.getTopic(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.disconnect();
        }
    }
}
