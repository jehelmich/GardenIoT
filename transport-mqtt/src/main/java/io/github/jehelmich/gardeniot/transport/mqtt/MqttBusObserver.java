package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A read-only view of everything that crosses the broker: telemetry, state, presence and
 * commands of every device. Meant for observers such as a monitoring page; it never publishes.
 */
public final class MqttBusObserver implements AutoCloseable {

    /** What kind of topic a message arrived on. */
    public enum Kind {
        TELEMETRY,
        STATE,
        STATUS,
        ALERT,
        COMMAND,
        FLEET_COMMAND
    }

    /**
     * @param kind     the topic family
     * @param deviceId the device the topic belongs to, or {@code null} for fleet commands
     * @param name     the state name or command name, or {@code null}
     * @param payload  the message body as UTF-8 text
     */
    public record BusMessage(Kind kind, String deviceId, String name, String payload) {}

    private final MqttSettings settings;
    private final MqttTopics topics;
    private Mqtt5AsyncClient client;

    public MqttBusObserver(MqttSettings settings) {
        this.settings = settings;
        this.topics = settings.topics();
    }

    public void start(Consumer<BusMessage> onMessage) throws Exception {
        client = MqttClients.connect(settings, "observer-" + UUID.randomUUID(), null, null);
        for (String filter : subscriptions()) {
            client.subscribeWith()
                    .topicFilter(filter)
                    .qos(MqttClients.QOS)
                    .callback(publish -> {
                        BusMessage message = classify(publish);
                        if (message != null) {
                            onMessage.accept(message);
                        }
                    })
                    .executor(MqttClients.CALLBACKS)
                    .send()
                    .get(30, TimeUnit.SECONDS);
        }
    }

    /** Every topic family the observer listens to; visible for tests. */
    List<String> subscriptions() {
        return List.of(
                topics.allTelemetry(),
                topics.allState(),
                topics.allStatus(),
                topics.allAlerts(),
                topics.allCommands(),
                topics.fleetCommand("+"));
    }

    BusMessage classify(Mqtt5Publish publish) {
        String topic = publish.getTopic().toString();
        String[] segments = topic.split("/");
        String payload = MqttClients.utf8(publish.getPayloadAsBytes());
        if (segments.length < 3 || !segments[0].equals(topics.prefix())) {
            return null;
        }
        if (segments[1].equals("_fleet") && segments.length == 4 && segments[2].equals("cmd")) {
            return new BusMessage(Kind.FLEET_COMMAND, null, segments[3], payload);
        }
        String deviceId = topics.deviceIdOf(topic);
        if (deviceId == null) {
            return null;
        }
        return switch (segments[2]) {
            case "telemetry" -> new BusMessage(Kind.TELEMETRY, deviceId, null, payload);
            case "status" -> new BusMessage(Kind.STATUS, deviceId, null, payload);
            case "state" -> segments.length == 4 ? new BusMessage(Kind.STATE, deviceId, segments[3], payload) : null;
            case "alert" -> segments.length == 4 ? new BusMessage(Kind.ALERT, deviceId, segments[3], payload) : null;
            case "cmd" -> segments.length == 4 ? new BusMessage(Kind.COMMAND, deviceId, segments[3], payload) : null;
            default -> null;
        };
    }

    @Override
    public void close() {
        if (client != null) {
            client.disconnect();
        }
    }
}
