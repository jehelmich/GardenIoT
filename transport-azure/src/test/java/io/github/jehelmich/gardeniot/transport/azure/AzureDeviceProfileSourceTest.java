package io.github.jehelmich.gardeniot.transport.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzureDeviceProfileSourceTest {

    @Test
    void readsTheProfileTheSdkHandsBackAsAMap() {
        WateringProfile profile =
                AzureDeviceProfileSource.parse(Map.of("name", "Fern", "minHumidity", 55.0, "maxHumidity", 90.0));

        assertThat(profile).isEqualTo(new WateringProfile("Fern", 55.0, 90.0));
    }

    @Test
    void rejectsAProfileThatMakesNoSense() {
        assertThatThrownBy(() ->
                        AzureDeviceProfileSource.parse(Map.of("name", "x", "minHumidity", 90.0, "maxHumidity", 10.0)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("min < max");
    }
}
