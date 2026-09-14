package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.DeviceTransport;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A device's connection to the broker. Each device has its own MQTT session so that the
 * broker's last-will mechanism can mark it offline individually.
 */
public final class MqttDeviceTransport implements DeviceTransport {

    private static final long PUBLISH_TIMEOUT_SECONDS = 30;

    private final Mqtt5AsyncClient client;
    private final MqttTopics topics;
    private final String deviceId;

    private MqttDeviceTransport(Mqtt5AsyncClient client, MqttTopics topics, String deviceId) {
        this.client = client;
        this.topics = topics;
        this.deviceId = deviceId;
    }

    static MqttDeviceTransport open(MqttSettings settings, String deviceId, CommandHandler handler) throws Exception {
        MqttTopics topics = settings.topics();
        Mqtt5AsyncClient client = MqttClients.connect(settings, "device-" + deviceId,
                topics.status(deviceId), MqttTopics.OFFLINE);
        MqttDeviceTransport transport = new MqttDeviceTransport(client, topics, deviceId);
        CommandResponder responder = new CommandResponder(client, handler);
        client.subscribeWith()
                .topicFilter(topics.deviceCommands(deviceId))
                .qos(MqttClients.QOS)
                .callback(responder::onRequest)
                .send()
                .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        transport.publishRetained(topics.status(deviceId), MqttTopics.ONLINE);
        return transport;
    }

    @Override
    public void publish(Telemetry telemetry) throws Exception {
        await(client.publishWith()
                .topic(topics.telemetry(deviceId))
                .qos(MqttClients.QOS)
                .contentType("application/json")
                .payload(MqttClients.utf8(TelemetryCodec.toJson(telemetry)))
                .send());
    }

    @Override
    public void reportState(String name, Object value) throws Exception {
        publishRetained(topics.state(deviceId, name), Json.stringify(value));
    }

    private void publishRetained(String topic, String payload) throws Exception {
        await(client.publishWith()
                .topic(topic)
                .qos(MqttClients.QOS)
                .retain(true)
                .payload(MqttClients.utf8(payload))
                .send());
    }

    private static void await(java.util.concurrent.CompletableFuture<?> publish)
            throws InterruptedException, TimeoutException, ExecutionException {
        publish.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        try {
            publishRetained(topics.status(deviceId), MqttTopics.OFFLINE);
        } catch (Exception e) {
            // The will would say the same; nothing else to do on the way out.
        }
        client.disconnect();
    }
}
