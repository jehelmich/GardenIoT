package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.transport.CommandResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceCommandsTest {

    private final RecordingTransport transport = new RecordingTransport();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private VirtualDevice device;

    @BeforeEach
    void connect() throws Exception {
        device = new VirtualDevice("basil", new PlantSimulation(15.0, 15.0, new Random(1L)),
                Duration.ofHours(1), Duration.ZERO, Clock.systemUTC(), scheduler, Runnable::run);
        device.start(transport);
    }

    @AfterEach
    void disconnect() {
        device.close();
        scheduler.shutdownNow();
    }

    @Test
    void waterIsAcceptedAndPerformed() {
        CommandResult result = transport.handler.handle("water", null);

        assertThat(result.status()).isEqualTo(202);
        assertThat(device.state().waterings()).isEqualTo(1);
    }

    @Test
    void rebootIsAccepted() {
        CommandResult result = transport.handler.handle("reboot", "{}");

        assertThat(result.status()).isEqualTo(202);
        assertThat(transport.state).containsKey("lastReboot");
    }

    @Test
    void setSpeedValidatesTheFactor() {
        assertThat(transport.handler.handle("setSpeed", "{\"factor\": 10}").status()).isEqualTo(200);
        assertThat(device.state().speed()).isEqualTo(10.0);

        assertThat(transport.handler.handle("setSpeed", "{\"factor\": 0}").status()).isEqualTo(400);
        assertThat(transport.handler.handle("setSpeed", "{\"factor\": \"fast\"}").status()).isEqualTo(400);
        assertThat(transport.handler.handle("setSpeed", null).status()).isEqualTo(400);
        assertThat(device.state().speed()).isEqualTo(10.0);
    }

    @Test
    void faultSwitchesTheSensorModel() {
        assertThat(transport.handler.handle("fault", "{\"type\": \"stuck\"}").status()).isEqualTo(200);
        assertThat(device.state().fault()).isEqualTo(SensorFault.STUCK);

        assertThat(transport.handler.handle("fault", "{\"type\": \"broken\"}").status()).isEqualTo(400);
        assertThat(transport.handler.handle("fault", "{\"type\": \"none\"}").status()).isEqualTo(200);
        assertThat(device.state().fault()).isEqualTo(SensorFault.NONE);
    }

    @Test
    void statusReturnsTheSimulatorsView() {
        CommandResult result = transport.handler.handle("status", null);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.payload()).isInstanceOf(SimulationState.class);
    }

    @Test
    void unknownCommandsAndBadJsonAreRejected() {
        assertThat(transport.handler.handle("selfDestruct", null).status()).isEqualTo(404);
        assertThat(transport.handler.handle("setSpeed", "{not json").status()).isEqualTo(400);
    }
}
