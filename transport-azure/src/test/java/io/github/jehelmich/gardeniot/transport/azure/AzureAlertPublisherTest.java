package io.github.jehelmich.gardeniot.transport.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.azure.sdk.iot.service.twin.Twin;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzureAlertPublisherTest {

    @Test
    void patchesOnlyTheOneAlertUnderDesiredProperties() {
        Twin raise = AzureAlertPublisher.patchFor("basil", "sensorStuck", Map.of("message", "frozen"));
        Twin clear = AzureAlertPublisher.patchFor("basil", "sensorStuck", null);

        assertThat(raise.getDeviceId()).isEqualTo("basil");
        assertThat(raise.getDesiredProperties()).containsOnlyKeys("alerts");
        assertThat(raise.getDesiredProperties().get("alerts"))
                .isEqualTo(Map.of("sensorStuck", Map.of("message", "frozen")));
        @SuppressWarnings("unchecked")
        Map<String, Object> cleared =
                (Map<String, Object>) clear.getDesiredProperties().get("alerts");
        assertThat(cleared).containsKey("sensorStuck");
        assertThat(cleared.get("sensorStuck")).isNull();
        assertThat(raise.getReportedProperties()).isEmpty();
    }
}
