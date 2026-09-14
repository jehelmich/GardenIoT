package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.device.Message;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AzureDeviceTransportTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    @Test
    void wrapsTheReadingAsAJsonMessage() {
        Telemetry telemetry = new Telemetry("garden-1", NOW, 22.4, 31.9);

        Message message = AzureDeviceTransport.toMessage(telemetry);

        assertThat(new String(message.getBytes(), StandardCharsets.UTF_8)).isEqualTo(TelemetryCodec.toJson(telemetry));
        assertThat(message.getContentType()).isEqualTo("application/json");
        assertThat(message.getContentEncoding()).isEqualTo("utf-8");
        assertThat(message.getMessageId()).isNotBlank();
    }

    @Test
    void flagsHotReadingsInAnApplicationProperty() {
        assertThat(AzureDeviceTransport.toMessage(new Telemetry("g", NOW, 30.0, 50.0)).getProperty("temperatureAlert"))
                .isEqualTo("false");
        assertThat(AzureDeviceTransport.toMessage(new Telemetry("g", NOW, 30.1, 50.0)).getProperty("temperatureAlert"))
                .isEqualTo("true");
    }
}
