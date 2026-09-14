package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.config.Environment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DeviceConfigTest {

    private static final String CONNECTION_STRING =
            "HostName=garden.azure-devices.net;DeviceId=garden-1;SharedAccessKey=c2VjcmV0";

    @Test
    void takesTheDeviceIdFromTheConnectionString() {
        assertThat(DeviceConfig.deviceIdOf(CONNECTION_STRING)).isEqualTo("garden-1");
        assertThat(DeviceConfig.deviceIdOf("deviceid=x; HostName=h")).isEqualTo("x");
    }

    @Test
    void rejectsAConnectionStringWithoutADeviceId() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DeviceConfig.deviceIdOf("HostName=h;SharedAccessKey=k"))
                .withMessageContaining("DeviceId");
        assertThatIllegalStateException()
                .isThrownBy(() -> DeviceConfig.deviceIdOf("HostName=h;DeviceId=;SharedAccessKey=k"));
    }

    @Test
    void appliesDefaultsForOptionalSettings() {
        DeviceConfig config = DeviceConfig.fromEnvironment(
                new Environment(Map.of(DeviceConfig.CONNECTION_STRING, CONNECTION_STRING)));

        assertThat(config.connectionString()).isEqualTo(CONNECTION_STRING);
        assertThat(config.deviceId()).isEqualTo("garden-1");
        assertThat(config.telemetryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.actionDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void readsOptionalSettings() {
        DeviceConfig config = DeviceConfig.fromEnvironment(new Environment(Map.of(
                DeviceConfig.CONNECTION_STRING, CONNECTION_STRING,
                DeviceConfig.TELEMETRY_INTERVAL, "1",
                DeviceConfig.ACTION_DURATION, "2")));

        assertThat(config.telemetryInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.actionDuration()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void requiresTheConnectionString() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DeviceConfig.fromEnvironment(new Environment(Map.of())))
                .withMessageContaining(DeviceConfig.CONNECTION_STRING);
    }
}
