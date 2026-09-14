package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.FleetChannel;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Fleet commands over a shared subscription, so one process out of many handles each. */
final class MqttFleetChannel implements FleetChannel {

    private final MqttSettings settings;
    private Mqtt5AsyncClient client;

    MqttFleetChannel(MqttSettings settings) {
        this.settings = settings;
    }

    @Override
    public void subscribe(CommandHandler handler) throws Exception {
        client = MqttClients.connect(settings, "fleet-" + UUID.randomUUID(), null, null);
        CommandResponder responder = new CommandResponder(client, handler);
        client.subscribeWith()
                .topicFilter(settings.topics().fleetCommandsShared())
                .qos(MqttClients.QOS)
                .callback(responder::onRequest)
                .send()
                .get(30, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        if (client != null) {
            client.disconnect();
        }
    }
}
