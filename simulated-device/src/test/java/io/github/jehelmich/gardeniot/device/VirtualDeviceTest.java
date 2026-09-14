package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.observability.Metrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class VirtualDeviceTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    private final RecordingTransport transport = new RecordingTransport();
    private final Metrics metrics = new Metrics();
    private final PlantSimulation plant = new PlantSimulation(15.0, 15.0, new Random(1L));
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    /** Runs actions inline so their outcome is visible immediately. */
    private final Executor actions = Runnable::run;
    private VirtualDevice device;

    @BeforeEach
    void connect() throws Exception {
        device = new VirtualDevice("basil", plant, Duration.ofHours(1), Duration.ZERO,
                Clock.fixed(NOW, ZoneOffset.UTC), scheduler, actions, new DeviceMetrics(metrics));
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
        double before = plant.current().humidity();

        device.startWatering();

        assertThat(plant.current().humidity()).isEqualTo(100.0).isGreaterThan(before);
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
                .contains("gardeniot_device_sensor_fault_code{device=\"basil\"} 2.0")
                .contains("gardeniot_device_true_humidity_percent{device=\"basil\"}")
                .contains("gardeniot_device_reported_humidity_percent{device=\"basil\"}");
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
