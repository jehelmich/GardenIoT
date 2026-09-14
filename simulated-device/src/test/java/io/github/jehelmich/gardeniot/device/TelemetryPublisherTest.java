package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TelemetryPublisherTest {

    private static final Instant NOW = Instant.parse("2017-07-17T10:15:30Z");

    private final PlantSimulation plant = new PlantSimulation(15.0, 15.0, new Random(1L));
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void publishesOneReadingPerRun() {
        List<Telemetry> published = new ArrayList<>();
        TelemetryPublisher publisher = new TelemetryPublisher("garden-1", plant, clock, published::add);

        publisher.run();
        publisher.run();

        assertThat(published).hasSize(2);
        assertThat(published.get(0).deviceId()).isEqualTo("garden-1");
        assertThat(published.get(0).timestamp()).isEqualTo(NOW);
        assertThat(published.get(1).humidity()).isLessThan(published.get(0).humidity());
    }

    @Test
    void aFailedSendIsLoggedNotThrown() {
        TelemetryPublisher publisher = new TelemetryPublisher("garden-1", plant, clock, telemetry -> {
            throw new IllegalStateException("hub unreachable");
        });

        assertThatNoException().isThrownBy(publisher::run);
    }

    @Test
    void anInterruptedSendRestoresTheInterruptFlag() throws InterruptedException {
        TelemetryPublisher publisher = new TelemetryPublisher("garden-1", plant, clock, telemetry -> {
            throw new InterruptedException();
        });

        Thread worker = new Thread(() -> {
            publisher.run();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        });
        worker.start();
        worker.join();
    }
}
