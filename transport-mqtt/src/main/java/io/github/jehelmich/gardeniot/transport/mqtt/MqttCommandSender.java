package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.CommandException;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.DeviceCommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends commands as MQTT 5 requests and waits for the reply.
 *
 * <p>One client, one reply topic; requests are matched to replies by correlation data. A
 * device that is offline simply never answers, which surfaces as a timeout — the same
 * experience as a direct method to a disconnected IoT Hub device.
 */
public final class MqttCommandSender implements DeviceCommandSender, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MqttCommandSender.class);

    private final MqttSettings settings;
    private final MqttTopics topics;
    private final String clientId = "commands-" + UUID.randomUUID();
    private final Map<String, CompletableFuture<CommandResult>> pending = new ConcurrentHashMap<>();
    private Mqtt5AsyncClient client;

    public MqttCommandSender(MqttSettings settings) {
        this.settings = settings;
        this.topics = settings.topics();
    }

    public MqttCommandSender start() throws Exception {
        client = MqttClients.connect(settings, clientId, null, null);
        client.subscribeWith()
                .topicFilter(topics.reply(clientId))
                .qos(MqttClients.QOS)
                .callback(this::onReply)
                .send()
                .get(30, TimeUnit.SECONDS);
        return this;
    }

    @Override
    public CommandResult send(String deviceId, String command, Object payload) throws CommandException {
        return request(topics.command(deviceId, command), command, payload);
    }

    /** Sends a command to the fleet rather than to one device. */
    public CommandResult sendToFleet(String command, Object payload) throws CommandException {
        return request(topics.fleetCommand(command), command, payload);
    }

    private CommandResult request(String topic, String command, Object payload) throws CommandException {
        if (client == null) {
            throw new IllegalStateException("start() first");
        }
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<CommandResult> reply = new CompletableFuture<>();
        pending.put(correlationId, reply);
        try {
            client.publishWith()
                    .topic(topic)
                    .qos(MqttClients.QOS)
                    .responseTopic(topics.reply(clientId))
                    .correlationData(MqttClients.utf8(correlationId))
                    .payload(MqttClients.utf8(payload == null ? "null" : Json.stringify(payload)))
                    .send()
                    .get(settings.commandTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return reply.get(settings.commandTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new CommandException("No answer to '" + command + "' on " + topic + " within "
                    + settings.commandTimeout().toSeconds() + "s");
        } catch (ExecutionException e) {
            throw new CommandException("Could not send '" + command + "' to " + topic + ": "
                    + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandException("Interrupted while waiting for '" + command + "'", e);
        } finally {
            pending.remove(correlationId);
        }
    }

    private void onReply(Mqtt5Publish publish) {
        String correlationId = publish.getCorrelationData()
                .map(MqttCommandSender::utf8)
                .orElse(null);
        CompletableFuture<CommandResult> reply = correlationId == null ? null : pending.get(correlationId);
        if (reply == null) {
            log.debug("Dropping reply with unknown correlation data {}", correlationId);
            return;
        }
        try {
            reply.complete(CommandReply.decode(MqttClients.utf8(publish.getPayloadAsBytes())));
        } catch (RuntimeException e) {
            reply.completeExceptionally(new CommandException("Unreadable reply: " + e.getMessage(), e));
        }
    }

    private static String utf8(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return MqttClients.utf8(bytes);
    }

    @Override
    public void close() {
        if (client != null) {
            client.disconnect();
        }
    }
}
