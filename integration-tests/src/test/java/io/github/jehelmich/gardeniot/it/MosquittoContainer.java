package io.github.jehelmich.gardeniot.it;

import io.github.jehelmich.gardeniot.transport.mqtt.MqttSettings;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Eclipse Mosquitto with the same configuration the compose file and the Helm chart use. */
final class MosquittoContainer extends GenericContainer<MosquittoContainer> {

    static final int MQTT_PORT = 1883;

    MosquittoContainer() {
        super(DockerImageName.parse("eclipse-mosquitto:2.0.22"));
        withExposedPorts(MQTT_PORT);
        withCopyFileToContainer(
                MountableFile.forHostPath("../deploy/mosquitto/mosquitto.conf"), "/mosquitto/config/mosquitto.conf");
        waitingFor(Wait.forLogMessage(".*mosquitto version .* running.*", 1));
    }

    MqttSettings settings() {
        return MqttSettings.local(getHost(), getMappedPort(MQTT_PORT));
    }
}
