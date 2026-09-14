package io.github.jehelmich.gardeniot.device;

import com.microsoft.azure.sdk.iot.device.twin.DirectMethodResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DirectMethodHandlerTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    private final PlantSimulation plant = new PlantSimulation(15.0, 15.0, new Random(1L));
    private final Map<String, Object> reported = new LinkedHashMap<>();

    /** Runs actions inline and without the simulated pump delay so the outcome is visible immediately. */
    private DirectMethodHandler handler(PropertyReporter reporter) {
        return new DirectMethodHandler(plant, reporter, Runnable::run, Duration.ZERO, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void waterAcceptsSoaksTheSoilAndReportsCompletion() {
        DirectMethodResponse response = handler(reported::put).onMethodInvoked("water", null, null);

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getPayload()).isEqualTo(Map.of("message", "Started watering"));
        assertThat(plant.current().humidity()).isEqualTo(100.0);
        assertThat(reported).containsExactly(Map.entry("lastWater", NOW.toString()));
    }

    @Test
    void rebootAcceptsAndReportsCompletion() {
        double humidityBefore = plant.current().humidity();

        DirectMethodResponse response = handler(reported::put).onMethodInvoked("reboot", null, null);

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(plant.current().humidity()).isEqualTo(humidityBefore);
        assertThat(reported).containsExactly(Map.entry("lastReboot", NOW.toString()));
    }

    @Test
    void unknownMethodsAreRejectedWithNotFound() {
        DirectMethodResponse response = handler(reported::put).onMethodInvoked("selfDestruct", null, null);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getPayload()).isEqualTo(Map.of("message", "Unknown method selfDestruct"));
        assertThat(reported).isEmpty();
    }

    @Test
    void aFailedTwinUpdateDoesNotPropagate() {
        DirectMethodHandler handler = handler((name, value) -> {
            throw new IllegalStateException("hub unreachable");
        });

        assertThatNoException().isThrownBy(() -> handler.onMethodInvoked("water", null, null));
        assertThat(plant.current().humidity()).isEqualTo(100.0);
    }
}
