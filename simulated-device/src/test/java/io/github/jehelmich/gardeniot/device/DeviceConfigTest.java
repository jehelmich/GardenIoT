package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.config.Transport;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeviceConfigTest {

    @Test
    void defaultsToMqttWithNoPresetPlants() {
        DeviceConfig config = DeviceConfig.fromEnvironment(new Environment(Map.of()));

        assertThat(config.transport()).isEqualTo(Transport.MQTT);
        assertThat(config.deviceIds()).isEmpty();
        assertThat(config.telemetryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.actionDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.weather().mode()).isEqualTo("clear");
        assertThat(config.wearMeanTicks()).isZero();
        assertThat(DeviceConfig.fromEnvironment(new Environment(Map.of(DeviceConfig.WEAR_MEAN_TICKS, "1500")))
                        .wearMeanTicks())
                .isEqualTo(1500);
    }

    @Test
    void readsTheWeatherSetting() {
        DeviceConfig config = DeviceConfig.fromEnvironment(new Environment(Map.of(
                DeviceConfig.WEATHER, "real",
                DeviceConfig.WEATHER_LATITUDE, "52.2",
                DeviceConfig.WEATHER_LONGITUDE, "0.12",
                DeviceConfig.WEATHER_PLACE, "Cambridge")));

        assertThat(config.weather()).isEqualTo(new WeatherProviders.Setting("real", 52.2, 0.12, "Cambridge"));
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
    void aReplicaPicksItsPlantByIndex() {
        Environment replica1 =
                new Environment(Map.of(DeviceConfig.PLANT_NAMES, "basil,mint", DeviceConfig.PLANT_INDEX, "1"));
        Environment replica5 =
                new Environment(Map.of(DeviceConfig.PLANT_NAMES, "basil,mint", DeviceConfig.PLANT_INDEX, "5"));
        Environment explicit = new Environment(Map.of(
                DeviceConfig.PLANT_NAMES,
                "basil,mint",
                DeviceConfig.PLANT_INDEX,
                "1",
                DeviceConfig.DEVICE_IDS,
                "thyme"));

        assertThat(DeviceConfig.fromEnvironment(replica1).deviceIds()).containsExactly("mint");
        assertThat(DeviceConfig.fromEnvironment(replica5).deviceIds()).isEmpty();
        assertThat(DeviceConfig.fromEnvironment(explicit).deviceIds()).containsExactly("thyme");
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
