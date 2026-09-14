package io.github.jehelmich.gardeniot.transport.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MqttTopicsTest {

    private final MqttTopics topics = new MqttTopics("garden");

    @Test
    void laysOutPerDeviceTopics() {
        assertThat(topics.telemetry("basil")).isEqualTo("garden/basil/telemetry");
        assertThat(topics.state("basil", "simulation")).isEqualTo("garden/basil/state/simulation");
        assertThat(topics.status("basil")).isEqualTo("garden/basil/status");
        assertThat(topics.alert("basil", "sensorStuck")).isEqualTo("garden/basil/alert/sensorStuck");
        assertThat(topics.allAlerts()).isEqualTo("garden/+/alert/+");
        assertThat(topics.command("basil", "water")).isEqualTo("garden/basil/cmd/water");
        assertThat(topics.deviceCommands("basil")).isEqualTo("garden/basil/cmd/+");
    }

    @Test
    void laysOutWildcardAndSystemTopics() {
        assertThat(topics.allTelemetry()).isEqualTo("garden/+/telemetry");
        assertThat(topics.allState()).isEqualTo("garden/+/state/+");
        assertThat(topics.fleetCommand("addPlant")).isEqualTo("garden/_fleet/cmd/addPlant");
        assertThat(topics.fleetCommandsShared()).isEqualTo("$share/fleet/garden/_fleet/cmd/+");
        assertThat(topics.reply("c1")).isEqualTo("garden/_reply/c1");
    }

    @Test
    void recoversTheDeviceIdFromATopic() {
        assertThat(topics.deviceIdOf("garden/basil/telemetry")).isEqualTo("basil");
        assertThat(topics.deviceIdOf("garden/basil/state/simulation")).isEqualTo("basil");
        assertThat(topics.deviceIdOf("garden/_fleet/cmd/addPlant")).isNull();
        assertThat(topics.deviceIdOf("other/basil/telemetry")).isNull();
        assertThat(topics.deviceIdOf("garden")).isNull();
        assertThat(MqttTopics.lastSegment("garden/basil/cmd/water")).isEqualTo("water");
    }
}
