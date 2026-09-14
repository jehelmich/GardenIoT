package io.github.jehelmich.gardeniot.it;

import io.github.jehelmich.gardeniot.config.Transport;
import io.github.jehelmich.gardeniot.controller.CommandWateringActuator;
import io.github.jehelmich.gardeniot.controller.TelemetryProcessor;
import io.github.jehelmich.gardeniot.controller.WateringPolicy;
import io.github.jehelmich.gardeniot.device.DeviceConfig;
import io.github.jehelmich.gardeniot.device.DeviceFleet;
import io.github.jehelmich.gardeniot.device.VirtualDevice;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.FleetChannel;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttCommandSender;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttDeviceTransportFactory;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttSettings;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttTelemetrySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole loop over a real broker: a plant dries out, the controller notices, commands
 * watering, the device answers and soaks the soil. Then the fleet channel adds a second plant
 * and a broken sensor stops the controller from watering it.
 */
@Testcontainers
class WateringLoopIT {

    @Container
    private static final MosquittoContainer BROKER = new MosquittoContainer();

    private static final Duration TICK = Duration.ofMillis(200);

    private MqttSettings settings;
    private MqttDeviceTransportFactory deviceTransports;
    private DeviceFleet fleet;
    private FleetChannel fleetChannel;
    private MqttTelemetrySource source;
    private MqttCommandSender commands;
    private final List<Telemetry> seen = new CopyOnWriteArrayList<>();
    private final AtomicReference<Throwable> streamFailure = new AtomicReference<>();

    @BeforeEach
    void startBothSides() throws Exception {
        settings = BROKER.settings();

        // device side
        deviceTransports = new MqttDeviceTransportFactory(settings);
        fleet = new DeviceFleet(new DeviceConfig(Transport.MQTT, List.of(), TICK, Duration.ZERO),
                deviceTransports, Clock.systemUTC());
        fleetChannel = deviceTransports.fleetChannel().orElseThrow();
        fleetChannel.subscribe(fleet);

        // cloud side
        commands = new MqttCommandSender(settings).start();
        WateringPolicy policy = new WateringPolicy(25.0, Duration.ofSeconds(2), Clock.systemUTC());
        TelemetryProcessor processor = new TelemetryProcessor(policy, new CommandWateringActuator(commands));
        source = new MqttTelemetrySource(settings);
        source.start(telemetry -> {
            seen.add(telemetry);
            processor.onTelemetry(telemetry);
        }, streamFailure::set);
    }

    @AfterEach
    void stopBothSides() {
        source.close();
        commands.close();
        fleetChannel.close();
        fleet.close();
        deviceTransports.close();
    }

    @Test
    void aDryPlantGetsWatered() throws Exception {
        VirtualDevice basil = fleet.add("basil");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(seen).anyMatch(t -> t.deviceId().equals("basil") && t.humidity() < 25.0));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(basil.state().waterings()).isGreaterThanOrEqualTo(1));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(seen).anyMatch(t -> t.deviceId().equals("basil") && t.humidity() > 90.0));
        assertThat(streamFailure.get()).isNull();
    }

    @Test
    void thePlantAnswersCommandsDirectly() throws Exception {
        fleet.add("mint");

        CommandResult status = commands.send("mint", "status", null);
        CommandResult speed = commands.send("mint", "setSpeed", java.util.Map.of("factor", 5));
        CommandResult unknown = commands.send("mint", "dance", null);

        assertThat(status.status()).isEqualTo(200);
        assertThat(status.payload().toString()).contains("trueHumidity");
        assertThat(speed.status()).isEqualTo(200);
        assertThat(speed.payload().toString()).contains("\"speed\":5");
        assertThat(unknown.status()).isEqualTo(404);
    }

    @Test
    void theFleetChannelAddsPlantsAndABrokenSensorGoesUnwatered() throws Exception {
        CommandResult added = commands.sendToFleet("addPlant", java.util.Map.of("deviceId", "thyme"));
        assertThat(added.status()).isEqualTo(200);
        assertThat(fleet.deviceIds()).contains("thyme");

        // Freeze the sensor at its first (damp) reading: the soil dries but the controller never learns.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(seen).anyMatch(t -> t.deviceId().equals("thyme")));
        assertThat(commands.send("thyme", "fault", java.util.Map.of("type", "STUCK")).status()).isEqualTo(200);

        Thread.sleep(TICK.toMillis() * 15);
        CommandResult status = commands.send("thyme", "status", null);
        assertThat(status.payload().toString()).contains("\"waterings\":0");
        assertThat(seen.stream().filter(t -> t.deviceId().equals("thyme")).map(Telemetry::humidity).distinct().count())
                .as("a stuck sensor repeats one value").isLessThanOrEqualTo(2);

        assertThat(commands.sendToFleet("removePlant", java.util.Map.of("deviceId", "thyme")).status()).isEqualTo(200);
        assertThat(fleet.deviceIds()).doesNotContain("thyme");
    }

}
