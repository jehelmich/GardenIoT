package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves one side of MQTT 5 request/response: runs the handler for each request and publishes
 * the result to the request's response topic, echoing its correlation data.
 */
final class CommandResponder {

    private static final Logger log = LoggerFactory.getLogger(CommandResponder.class);

    private final Mqtt5AsyncClient client;
    private final CommandHandler handler;

    CommandResponder(Mqtt5AsyncClient client, CommandHandler handler) {
        this.client = client;
        this.handler = handler;
    }

    void onRequest(Mqtt5Publish request) {
        String command = MqttTopics.lastSegment(request.getTopic().toString());
        String payload = request.getPayload().isPresent() ? MqttClients.utf8(request.getPayloadAsBytes()) : null;
        CommandResult result;
        try {
            result = handler.handle(command, payload);
        } catch (RuntimeException e) {
            log.warn("Command '{}' failed: {}", command, e.toString());
            result = new CommandResult(CommandResult.FAILED, java.util.Map.of("message", e.toString()));
        }
        if (request.getResponseTopic().isEmpty()) {
            return; // fire-and-forget
        }
        var publish = client.publishWith()
                .topic(request.getResponseTopic().get())
                .qos(MqttClients.QOS)
                .payload(MqttClients.utf8(CommandReply.encode(result)));
        request.getCorrelationData().map(ByteBuffer::duplicate).ifPresent(publish::correlationData);
        publish.send().whenComplete((ack, error) -> {
            if (error != null) {
                log.warn("Could not answer command '{}': {}", command, error.getMessage());
            }
        });
    }
}
