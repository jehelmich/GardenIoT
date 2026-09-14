package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds and connects clients with the settings every adapter shares. */
final class MqttClients {

    private static final Logger log = LoggerFactory.getLogger(MqttClients.class);

    static final MqttQos QOS = MqttQos.AT_LEAST_ONCE;
    private static final long CONNECT_TIMEOUT_SECONDS = 30;

    private MqttClients() {}

    /**
     * @param willTopic if non-null, the broker publishes {@code willPayload} there, retained,
     *                  should the client vanish without disconnecting
     */
    static Mqtt5AsyncClient connect(MqttSettings settings, String clientId, String willTopic, String willPayload)
            throws InterruptedException, TimeoutException {
        Mqtt5ClientBuilder builder = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(settings.host())
                .serverPort(settings.port())
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(
                        context -> log.info("{}: connected to {}:{}", clientId, settings.host(), settings.port()))
                .addDisconnectedListener(context -> {
                    if (context.getReconnector().isReconnect()) {
                        log.warn(
                                "{}: disconnected ({}), reconnecting",
                                clientId,
                                context.getCause().getMessage());
                    }
                });
        if (settings.tls()) {
            builder = builder.sslWithDefaultConfig();
        }
        if (settings.username().isPresent()) {
            builder = builder.simpleAuth()
                    .username(settings.username().get())
                    .password(settings.password().orElse("").getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }
        if (willTopic != null) {
            builder = builder.willPublish()
                    .topic(willTopic)
                    .payload(willPayload.getBytes(StandardCharsets.UTF_8))
                    .qos(QOS)
                    .retain(true)
                    .applyWillPublish();
        }
        Mqtt5AsyncClient client = builder.buildAsync();
        try {
            client.connect().get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Could not connect to MQTT broker at " + settings.host() + ":" + settings.port() + ": "
                            + e.getCause().getMessage(),
                    e.getCause());
        }
        return client;
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
