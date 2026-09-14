package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.jehelmich.gardeniot.observability.Metrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualDeviceTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    private final RecordingTransport transport = new RecordingTransport();
    private final Metrics metrics = new Metrics();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    /** Runs actions inline so their outcome is visible immediately. */
    private final Executor actions = Runnable::run;

    private VirtualDevice device;

    @BeforeEach
    void connect() throws Exception {
        device = new VirtualDevice(
                "basil",
                PlantProfile.DEFAULT,
                WeatherProvider.fixed(WeatherConditions.CLEAR),
                new Random(1L),
                Duration.ofHours(1),
                Duration.ZERO,
                0,
                Clock.fixed(NOW, ZoneOffset.UTC),
                scheduler,
                actions,
                new DeviceMetrics(metrics));
        device.start(transport);
        // The first tick is scheduled immediately; wait for it so tests start from a known count.
        IntStream.range(0, 100).takeWhile(i -> transport.published.isEmpty()).forEach(i -> sleep(10));
        transport.published.clear();
    }

    @AfterEach
    void disconnect() {
        device.close();
        scheduler.shutdownNow();
    }

    @Test
    void aTickPublishesTheSensorReadingAndTheSimulationState() {
        device.tick();

        assertThat(transport.published).hasSize(1);
        assertThat(transport.published.get(0).deviceId()).isEqualTo("basil");
        assertThat(transport.published.get(0).timestamp()).isEqualTo(NOW);
        assertThat(transport.state).containsKey(SimulationState.NAME);
        SimulationState state = (SimulationState) transport.state.get(SimulationState.NAME);
        assertThat(state.trueHumidity()).isEqualTo(transport.published.get(0).humidity());
    }

    @Test
    void wateringSoaksTheSoilAndCountsTheWatering() {
        IntStream.range(0, 20).forEach(i -> device.tick());
        double before = device.plant().current().humidity();

        device.startWatering();

        assertThat(device.plant().current().humidity()).isGreaterThan(before);
        SimulationState state = (SimulationState) transport.state.get(SimulationState.NAME);
        assertThat(state.waterings()).isEqualTo(1);
        assertThat(state.lastWatered()).isEqualTo(NOW);
    }

    @Test
    void aStuckSensorRepeatsTheLastReadingWhileTheSoilDries() {
        device.tick();
        double frozen = transport.published.get(0).humidity();

        device.setFault(SensorFault.STUCK);
        IntStream.range(0, 5).forEach(i -> device.tick());

        assertThat(transport.published).extracting("humidity").containsOnly(frozen);
        SimulationState state = (SimulationState) transport.state.get(SimulationState.NAME);
        assertThat(state.trueHumidity()).isLessThan(frozen);
        assertThat(state.fault()).isEqualTo(SensorFault.STUCK);
    }

    @Test
    void anOverreadingSensorMakesDrySoilLookDamp() {
        device.setFault(SensorFault.OVERREAD);

        device.tick();

        SimulationState state = (SimulationState) transport.state.get(SimulationState.NAME);
        assertThat(transport.published.get(0).humidity()).isGreaterThan(state.trueHumidity() + 30.0);
    }

    @Test
    void aSilentSensorPublishesNothingButTheSimulationCarriesOn() {
        device.setFault(SensorFault.SILENT);

        device.tick();
        device.tick();

        assertThat(transport.published).isEmpty();
        assertThat(transport.state).containsKey(SimulationState.NAME);
    }

    @Test
    void aFailedPublishIsLoggedNotThrown() {
        transport.failure = new IllegalStateException("broker gone");

        assertThatNoException().isThrownBy(device::tick);
    }

    @Test
    void exposesTheTruthNextToTheReading() {
        device.setFault(SensorFault.OVERREAD);

        device.tick();

        assertThat(metrics.scrape())
                .contains("gardeniot_device_sensor_fault_code{device=\"basil\"} 3.0")
                .contains("gardeniot_device_true_humidity_percent{device=\"basil\"}")
                .contains("gardeniot_device_reported_humidity_percent{device=\"basil\"}");
    }

    @Test
    void reportsItsWateringProfileOnConnect() {
        assertThat(transport.state).containsKey("profile");
        assertThat(transport.state.get("profile").toString()).contains("Basil");
    }

    @Test
    void theTechnicianRepairsTheSensorAndThePump() {
        device.setFault(SensorFault.STUCK);
        device.setPumpFailed(true);

        assertThat(device.callTechnician()).isTrue();

        assertThat(device.state().fault()).isEqualTo(SensorFault.NONE);
        assertThat(device.state().pump()).isEqualTo(VirtualDevice.PUMP_OK);
        assertThat(device.state().job()).isNull();
    }

    @Test
    void aFailedPumpIsNoticedByTheDeviceItself() {
        device.setPumpFailed(true);
        double before = device.plant().current().humidity();

        assertThat(device.startWatering()).isFalse();

        assertThat(device.plant().current().humidity()).isEqualTo(before);
        assertThat(device.state().waterings()).isZero();
        assertThat(transport.handler.handle("water", null).status()).isEqualTo(500);
    }

    @Test
    void wearBreaksThingsOnItsOwnButOnlyOneAtATime() {
        device.setWear(1); // every reading

        device.tick();
        SimulationState broken = device.state();
        assertThat(broken.fault() != SensorFault.NONE || broken.pump().equals(VirtualDevice.PUMP_FAILED))
                .isTrue();

        IntStream.range(0, 20).forEach(i -> device.tick());
        SimulationState later = device.state();
        assertThat(later.fault()).isEqualTo(broken.fault());
        assertThat(later.pump()).isEqualTo(broken.pump());
    }

    @Test
    void aDriftingSensorLiesMoreWithEveryReading() {
        device.setFault(SensorFault.DRIFT);

        device.tick();
        double first = transport.published.get(0).humidity() - device.state().trueHumidity();
        IntStream.range(0, 50).forEach(i -> device.tick());
        double later = transport.published.get(transport.published.size() - 1).humidity()
                - device.state().trueHumidity();

        assertThat(later).isGreaterThan(first + 5.0);
    }

    @Test
    void repottingPutsAFreshSeedlingInThePot() {
        IntStream.range(0, 3_000).forEach(i -> device.tick());
        assertThat(device.state().alive()).isFalse();

        assertThat(device.repot()).isTrue();

        assertThat(device.state().alive()).isTrue();
        assertThat(device.state().repots()).isEqualTo(1);
        assertThat(device.state().profile()).isEqualTo("basil");
    }

    @Test
    void weatherChangesShowUpInTheState() {
        device.setWeather(WeatherProvider.fixed(WeatherConditions.RAIN));

        assertThat(device.state().weather()).isEqualTo(WeatherConditions.Kind.RAIN);
        assertThat(device.state().weatherLabel()).isEqualTo("Rain, 15 °C");
    }

    @Test
    void closingDisconnectsTheTransport() {
        device.close();

        assertThat(transport.closed).isTrue();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
