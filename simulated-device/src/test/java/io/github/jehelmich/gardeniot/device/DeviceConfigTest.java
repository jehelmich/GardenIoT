package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.config.Transport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DeviceConfigTest {

    @Test
    void defaultsToMqttWithNoPresetPlants() {
        DeviceConfig config = DeviceConfig.fromEnvironment(new Environment(Map.of()));

        assertThat(config.transport()).isEqualTo(Transport.MQTT);
        assertThat(config.deviceIds()).isEmpty();
        assertThat(config.telemetryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.actionDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void parsesACommaSeparatedListOfPlants() {
        DeviceConfig config = DeviceConfig.fromEnvironment(new Environment(Map.of(
                DeviceConfig.DEVICE_IDS, " basil, mint ,,thyme-2 ",
                DeviceConfig.TELEMETRY_INTERVAL, "1")));

        assertThat(config.deviceIds()).containsExactly("basil", "mint", "thyme-2");
        assertThat(config.telemetryInterval()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsIdsThatWouldBreakTopicsOrUrls() {
        assertThatIllegalArgumentException().isThrownBy(() -> DeviceConfig.parseIds("a/b"));
        assertThatIllegalArgumentException().isThrownBy(() -> DeviceConfig.parseIds("a b"));
        assertThatIllegalArgumentException().isThrownBy(() -> DeviceConfig.parseIds("-leading"));
        assertThatIllegalArgumentException().isThrownBy(() -> DeviceConfig.parseIds("x".repeat(65)));
    }

    @Test
    void derivesADefaultIdFromTheHostName() {
        assertThat(DeviceApp.defaultDeviceId()).matches("plant-[a-z0-9._-]{1,20}");
    }
}
