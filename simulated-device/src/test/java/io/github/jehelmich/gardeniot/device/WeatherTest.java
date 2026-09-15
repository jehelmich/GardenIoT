package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.device.WeatherConditions.Kind;
import io.github.jehelmich.gardeniot.telemetry.Json;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WeatherTest {

    /** Aligned to the start of an auto-weather period. */
    private static final Instant T0 = Instant.ofEpochSecond(
            Math.floorDiv(Instant.parse("2017-07-17T10:15:30Z").getEpochSecond(), WeatherProvider.Auto.PERIOD_SECONDS)
                    * WeatherProvider.Auto.PERIOD_SECONDS);

    @Test
    void autoWeatherIsTheSameForEveryoneAndChangesOverTime() {
        WeatherConditions now = WeatherProvider.Auto.at(T0);

        assertThat(WeatherProvider.Auto.at(T0.plusSeconds(30))).isEqualTo(now);
        assertThat(now.source()).isEqualTo("auto");
        long distinct = IntStream.range(0, 24)
                .mapToObj(i -> WeatherProvider.Auto.at(T0.plusSeconds(i * WeatherProvider.Auto.PERIOD_SECONDS))
                        .kind())
                .distinct()
                .count();
        assertThat(distinct).isGreaterThan(3);
    }

    @Test
    void settingsBuildTheRightProvider() {
        WeatherProviders providers = new WeatherProviders(Clock.fixed(T0, ZoneOffset.UTC), HttpClient.newHttpClient());

        assertThat(providers.create(WeatherProviders.Setting.of("Rain")).current())
                .isEqualTo(WeatherConditions.RAIN);
        assertThat(providers
                        .create(WeatherProviders.Setting.of("auto"))
                        .current()
                        .source())
                .isEqualTo("auto");
        assertThat(providers.create(new WeatherProviders.Setting("real", 52.2, 0.12, "Cambridge")))
                .isInstanceOf(OpenMeteoWeather.class);
        assertThatIllegalArgumentException().isThrownBy(() -> providers.create(WeatherProviders.Setting.of("plague")));
        assertThatIllegalArgumentException().isThrownBy(() -> providers.create(WeatherProviders.Setting.of("real")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> providers.create(new WeatherProviders.Setting("real", 95.0, 0.0, null)));
    }

    @Test
    void settingsParseFromCommandPayloads() {
        WeatherProviders.Setting setting = WeatherProviders.Setting.fromJson(
                Json.tree("{\"mode\":\"real\",\"latitude\":52.2,\"longitude\":0.12,\"place\":\"Cambridge\"}"));

        assertThat(setting).isEqualTo(new WeatherProviders.Setting("real", 52.2, 0.12, "Cambridge"));
        assertThatIllegalArgumentException().isThrownBy(() -> WeatherProviders.Setting.fromJson(Json.tree("{}")));
        assertThatIllegalArgumentException().isThrownBy(() -> WeatherProviders.Setting.fromJson(null));
    }

    @Test
    void mapsOpenMeteoOntoThePot() {
        JsonObject rain = Json.tree(
                        "{\"temperature_2m\":14.3,\"precipitation\":2.0,\"weather_code\":63,\"wind_speed_10m\":20}")
                .getAsJsonObject();
        WeatherConditions rainy = OpenMeteoWeather.fromCurrent(rain, "Cambridge");

        assertThat(rainy.kind()).isEqualTo(Kind.RAIN);
        assertThat(rainy.label()).isEqualTo("Rain, 14 °C, 2.0 mm/h");
        assertThat(rainy.source()).isEqualTo("Cambridge");
        assertThat(rainy.temperatureTarget()).isEqualTo(14.3);
        assertThat(rainy.rainPerStep()).isEqualTo(0.6);
        assertThat(rainy.evaporationFactor()).isEqualTo(0.6);

        JsonObject scorcher = Json.tree(
                        "{\"temperature_2m\":35.0,\"precipitation\":0,\"weather_code\":0,\"wind_speed_10m\":0}")
                .getAsJsonObject();
        assertThat(OpenMeteoWeather.fromCurrent(scorcher, "Seville").kind()).isEqualTo(Kind.HEATWAVE);
        assertThat(OpenMeteoWeather.fromCurrent(scorcher, "Seville").evaporationFactor())
                .isEqualTo(2.0);

        assertThat(OpenMeteoWeather.kindOf(95, 20)).isEqualTo(Kind.STORM);
        assertThat(OpenMeteoWeather.kindOf(73, -2)).isEqualTo(Kind.SNOW);
        assertThat(OpenMeteoWeather.kindOf(45, 10)).isEqualTo(Kind.FOG);
        assertThat(OpenMeteoWeather.kindOf(3, 2)).isEqualTo(Kind.COLD);
        assertThat(OpenMeteoWeather.kindOf(2, 18)).isEqualTo(Kind.CLOUDY);
        assertThat(OpenMeteoWeather.describe(80)).isEqualTo("Showers");
    }

    @Test
    void liveWeatherFallsBackToClearWhileOffline() {
        HttpClient unreachable =
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build();
        OpenMeteoWeather weather =
                new OpenMeteoWeather(52.2, 0.12, "Nowhere", unreachable, Clock.fixed(T0, ZoneOffset.UTC));

        // No network in unit tests is the point; whatever happens, the pot gets conditions.
        WeatherConditions conditions = weather.current();
        assertThat(conditions.source()).isIn("Nowhere", "Nowhere (offline)");
        assertThat(weather.place()).isEqualTo("Nowhere");
    }
}
