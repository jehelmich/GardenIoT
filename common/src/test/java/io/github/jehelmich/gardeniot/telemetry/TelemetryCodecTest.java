package io.github.jehelmich.gardeniot.telemetry;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TelemetryCodecTest {

    private static final Telemetry READING =
            new Telemetry("garden-1", Instant.parse("2017-07-17T10:15:30Z"), 22.4, 31.9);

    @Test
    void producesTheDocumentedWireFormat() {
        assertThat(TelemetryCodec.toJson(READING)).isEqualTo(
                "{\"deviceId\":\"garden-1\",\"timestamp\":\"2017-07-17T10:15:30Z\",\"temperature\":22.4,\"humidity\":31.9}");
    }

    @Test
    void roundTripsThroughJson() {
        assertThat(TelemetryCodec.fromJson(TelemetryCodec.toJson(READING))).isEqualTo(READING);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TelemetryCodec.fromJson("{not json"))
                .withMessageStartingWith("Malformed telemetry document");
    }

    @Test
    void rejectsAnEmptyDocument() {
        assertThatIllegalArgumentException().isThrownBy(() -> TelemetryCodec.fromJson("null"));
    }

    @Test
    void rejectsMissingFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TelemetryCodec.fromJson("{\"temperature\":1,\"humidity\":2}"))
                .withMessageContaining("deviceId");
    }

    @Test
    void rejectsATimestampThatIsNotAnInstant() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TelemetryCodec.fromJson(
                        "{\"deviceId\":\"g\",\"timestamp\":\"yesterday\",\"temperature\":1,\"humidity\":2}"))
                .withMessageContaining("ISO-8601");
    }

    @Test
    void rejectsHumidityOutOfRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TelemetryCodec.fromJson(
                        "{\"deviceId\":\"g\",\"timestamp\":\"2017-07-17T10:15:30Z\",\"temperature\":1,\"humidity\":120}"))
                .withMessageContaining("humidity");
    }
}
