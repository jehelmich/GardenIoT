package io.github.jehelmich.gardeniot.transport.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.messaging.eventhubs.EventData;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AzureTelemetrySourceTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    @Test
    void decodesAReading() {
        Telemetry telemetry = new Telemetry("garden-1", NOW, 22.0, 40.0);

        assertThat(AzureTelemetrySource.decode(new EventData(TelemetryCodec.toJson(telemetry))))
                .contains(telemetry);
    }

    @Test
    void dropsWhatItCannotRead() {
        assertThat(AzureTelemetrySource.decode(new EventData("not telemetry"))).isEmpty();
    }
}
