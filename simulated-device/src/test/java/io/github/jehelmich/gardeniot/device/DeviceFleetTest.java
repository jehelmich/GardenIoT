package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jehelmich.gardeniot.config.Transport;
import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeviceFleetTest {

    private final RecordingTransport transport = new RecordingTransport();
    private final DeviceFleet fleet = new DeviceFleet(
            new DeviceConfig(Transport.MQTT, List.of(), Duration.ofHours(1), Duration.ZERO),
            transport,
            Clock.systemUTC(),
            new DeviceMetrics(new Metrics()));

    @AfterEach
    void close() {
        fleet.close();
    }

    @Test
    void addsAndRemovesPlantsOnCommand() {
        CommandResult added = fleet.handle("addPlant", "{\"deviceId\": \"basil\"}");

        assertThat(added.status()).isEqualTo(200);
        assertThat(transport.connected).containsExactly("basil");
        assertThat(fleet.deviceIds()).containsExactly("basil");

        assertThat(fleet.handle("addPlant", "{\"deviceId\": \"basil\"}").status())
                .isEqualTo(409);
        assertThat(fleet.handle("removePlant", "{\"deviceId\": \"basil\"}").status())
                .isEqualTo(200);
        assertThat(fleet.handle("removePlant", "{\"deviceId\": \"basil\"}").status())
                .isEqualTo(404);
        assertThat(fleet.deviceIds()).isEmpty();
        assertThat(transport.closed).isTrue();
    }

    @Test
    void validatesDeviceIds() {
        assertThat(fleet.handle("addPlant", "{\"deviceId\": \"no spaces\"}").status())
                .isEqualTo(400);
        assertThat(fleet.handle("addPlant", "{\"deviceId\": \"a/b\"}").status()).isEqualTo(400);
        assertThat(fleet.handle("addPlant", "{}").status()).isEqualTo(400);
        assertThat(fleet.handle("addPlant", null).status()).isEqualTo(400);
        assertThat(fleet.deviceIds()).isEmpty();
    }

    @Test
    void listsWhatItHosts() throws Exception {
        fleet.add("basil");
        fleet.add("mint");

        CommandResult result = fleet.handle("listPlants", null);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.payload().toString()).contains("basil", "mint");
        assertThat(fleet.handle("dance", null).status()).isEqualTo(404);
    }
}
