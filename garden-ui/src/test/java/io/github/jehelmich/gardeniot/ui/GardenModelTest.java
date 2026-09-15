package io.github.jehelmich.gardeniot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver.BusMessage;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver.Kind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GardenModelTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");
    private static final String READING =
            "{\"deviceId\":\"basil\",\"timestamp\":\"2017-07-17T10:15:30Z\",\"temperature\":22.0,\"humidity\":31.0}";

    private final GardenModel model = new GardenModel(Clock.fixed(NOW, ZoneOffset.UTC));
    private final List<String> events = new ArrayList<>();

    GardenModelTest() {
        model.addListener(events::add);
    }

    @Test
    void buildsAPlantFromWhatItSeesOnTheBus() {
        model.apply(new BusMessage(Kind.STATUS, "basil", null, "online"));
        model.apply(new BusMessage(Kind.TELEMETRY, "basil", null, READING));
        model.apply(new BusMessage(Kind.STATE, "basil", "simulation", "{\"trueHumidity\":12.5,\"fault\":\"STUCK\"}"));
        model.apply(new BusMessage(Kind.COMMAND, "basil", "water", null));

        JsonObject snapshot = Json.tree(model.snapshot()).getAsJsonObject();
        JsonObject basil = snapshot.getAsJsonArray("plants").get(0).getAsJsonObject();

        assertThat(snapshot.get("type").getAsString()).isEqualTo("snapshot");
        assertThat(basil.get("deviceId").getAsString()).isEqualTo("basil");
        assertThat(basil.get("online").getAsBoolean()).isTrue();
        assertThat(basil.getAsJsonObject("telemetry").get("humidity").getAsDouble())
                .isEqualTo(31.0);
        assertThat(basil.getAsJsonObject("state")
                        .getAsJsonObject("simulation")
                        .get("fault")
                        .getAsString())
                .isEqualTo("STUCK");
        assertThat(basil.get("lastCommand").getAsString()).isEqualTo("water");
        assertThat(events).hasSize(4);
        assertThat(events.get(3)).contains("\"type\":\"command\"").contains("\"command\":\"water\"");
    }

    @Test
    void ignoresWhatItCannotReadAndDropsForgottenPlants() {
        model.apply(new BusMessage(Kind.TELEMETRY, "basil", null, "<garbage>"));
        model.apply(new BusMessage(Kind.STATE, "mint", "simulation", "{not json"));

        assertThat(events).isEmpty();
        assertThat(model.deviceIds()).as("garbage does not conjure up a plant").isEmpty();

        model.apply(new BusMessage(Kind.STATUS, "mint", null, "offline"));
        assertThat(model.deviceIds()).containsExactly("mint");

        model.forget("mint");
        assertThat(model.deviceIds()).isEmpty();
        assertThat(events.get(events.size() - 1)).contains("\"type\":\"removed\"");
        model.forget("mint");
        assertThat(events).as("forgetting twice is silent").hasSize(2);
    }

    @Test
    void keepsTheControllersAlertsUntilTheyAreCleared() {
        model.apply(new BusMessage(Kind.ALERT, "basil", "sensorStuck", "{\"message\":\"frozen\"}"));

        JsonObject basil = Json.tree(model.snapshot())
                .getAsJsonObject()
                .getAsJsonArray("plants")
                .get(0)
                .getAsJsonObject();
        assertThat(basil.getAsJsonObject("alerts")
                        .getAsJsonObject("sensorStuck")
                        .get("message")
                        .getAsString())
                .isEqualTo("frozen");
        assertThat(events.get(0)).contains("\"type\":\"alert\"").contains("sensorStuck");

        model.apply(new BusMessage(Kind.ALERT, "basil", "sensorStuck", ""));
        basil = Json.tree(model.snapshot())
                .getAsJsonObject()
                .getAsJsonArray("plants")
                .get(0)
                .getAsJsonObject();
        assertThat(basil.getAsJsonObject("alerts").size()).isZero();
    }

    @Test
    void fleetCommandsAreEventsWithoutAPlant() {
        model.apply(new BusMessage(Kind.FLEET_COMMAND, null, "addPlant", "{\"deviceId\":\"thyme\"}"));

        assertThat(model.deviceIds()).isEmpty();
        assertThat(events.get(0)).contains("\"type\":\"fleet\"").contains("thyme");
    }
}
