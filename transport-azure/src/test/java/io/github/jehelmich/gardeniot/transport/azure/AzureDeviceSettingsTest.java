package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import io.github.jehelmich.gardeniot.config.Environment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AzureDeviceSettingsTest {

    private static final String CONNECTION_STRING =
            "HostName=garden.azure-devices.net;DeviceId=garden-1;SharedAccessKey=c2VjcmV0";

    @Test
    void takesTheDeviceIdFromTheConnectionString() {
        assertThat(AzureDeviceSettings.deviceIdOf(CONNECTION_STRING)).isEqualTo("garden-1");
        assertThat(AzureDeviceSettings.deviceIdOf("deviceid=x; HostName=h")).isEqualTo("x");
    }

    @Test
    void rejectsAConnectionStringWithoutADeviceId() {
        assertThatIllegalStateException()
                .isThrownBy(() -> AzureDeviceSettings.deviceIdOf("HostName=h;SharedAccessKey=k"))
                .withMessageContaining("DeviceId");
        assertThatIllegalStateException()
                .isThrownBy(() -> AzureDeviceSettings.deviceIdOf("HostName=h;DeviceId=;SharedAccessKey=k"));
    }

    @Test
    void defaultsToMqtt() {
        AzureDeviceSettings settings = AzureDeviceSettings.fromEnvironment(
                new Environment(Map.of(AzureDeviceSettings.CONNECTION_STRING, CONNECTION_STRING)));

        assertThat(settings.deviceId()).isEqualTo("garden-1");
        assertThat(settings.protocol()).isEqualTo(IotHubClientProtocol.MQTT);
    }

    @Test
    void acceptsTheOtherProtocolsCaseInsensitively() {
        AzureDeviceSettings settings = AzureDeviceSettings.fromEnvironment(new Environment(Map.of(
                AzureDeviceSettings.CONNECTION_STRING, CONNECTION_STRING,
                AzureDeviceSettings.PROTOCOL, "mqtt_ws")));

        assertThat(settings.protocol()).isEqualTo(IotHubClientProtocol.MQTT_WS);
        assertThatIllegalStateException().isThrownBy(() -> AzureDeviceSettings.fromEnvironment(new Environment(Map.of(
                AzureDeviceSettings.CONNECTION_STRING, CONNECTION_STRING,
                AzureDeviceSettings.PROTOCOL, "smoke-signals"))));
    }

    @Test
    void requiresTheConnectionString() {
        assertThatIllegalStateException()
                .isThrownBy(() -> AzureDeviceSettings.fromEnvironment(new Environment(Map.of())))
                .withMessageContaining(AzureDeviceSettings.CONNECTION_STRING);
    }
}
