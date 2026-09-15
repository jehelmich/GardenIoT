package io.github.jehelmich.gardeniot.transport.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import io.github.jehelmich.gardeniot.transport.DeviceProfileSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watering profiles from the devices' retained {@code state/profile} topics. The broker replays
 * retained messages on subscription, so a freshly started controller knows every plant's needs
 * before the first reading arrives.
 */
public final class MqttDeviceProfileSource implements DeviceProfileSource, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceProfileSource.class);

    private final MqttSettings settings;
    private final MqttTopics topics;
    private final Map<String, WateringProfile> profiles = new ConcurrentHashMap<>();
    private Mqtt5AsyncClient client;

    public MqttDeviceProfileSource(MqttSettings settings) {
        this.settings = settings;
        this.topics = settings.topics();
    }

    public MqttDeviceProfileSource start() throws Exception {
        client = MqttClients.connect(settings, "profiles-" + UUID.randomUUID(), null, null);
        client.subscribeWith()
                .topicFilter(topics.prefix() + "/+/state/" + WateringProfile.STATE_NAME)
                .qos(MqttClients.QOS)
                .callback(this::onProfile)
                .executor(MqttClients.CALLBACKS)
                .send()
                .get(30, TimeUnit.SECONDS);
        return this;
    }

    void onProfile(Mqtt5Publish publish) {
        String deviceId = topics.deviceIdOf(publish.getTopic().toString());
        if (deviceId == null) {
            return;
        }
        String payload = MqttClients.utf8(publish.getPayloadAsBytes());
        if (payload.isBlank()) {
            profiles.remove(deviceId); // an empty retained message clears the topic
            return;
        }
        try {
            WateringProfile profile = Json.parse(payload, WateringProfile.class);
            profiles.put(deviceId, profile);
            log.info(
                    "{}: {} wants {}–{}% humidity",
                    deviceId, profile.name(), profile.minHumidity(), profile.maxHumidity());
        } catch (RuntimeException e) {
            log.warn("{}: unreadable profile: {}", deviceId, e.getMessage());
        }
    }

    @Override
    public Optional<WateringProfile> profileOf(String deviceId) {
        return Optional.ofNullable(profiles.get(deviceId));
    }

    @Override
    public void close() {
        if (client != null) {
            client.disconnect();
        }
    }
}
