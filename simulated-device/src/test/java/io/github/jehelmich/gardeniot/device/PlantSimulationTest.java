package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlantSimulationTest {

    private static final long SEED = 42L;

    @Test
    void soilDriesOutWithEveryReading() {
        PlantSimulation plant = new PlantSimulation(15.0, 15.0, new Random(SEED));
        double before = plant.current().humidity();

        double after = plant.next().humidity();

        assertThat(after).isLessThan(before);
    }

    @Test
    void soilNeverDriesBelowTheMinimum() {
        PlantSimulation plant = new PlantSimulation(15.0, 20.0, new Random(SEED));

        IntStream.range(0, 10_000).forEach(i -> plant.next());

        assertThat(plant.current().humidity()).isEqualTo(20.0);
    }

    @Test
    void wateringSoaksTheSoil() {
        PlantSimulation plant = new PlantSimulation(15.0, 15.0, new Random(SEED));
        IntStream.range(0, 50).forEach(i -> plant.next());

        plant.water();

        assertThat(plant.current().humidity()).isEqualTo(100.0);
        assertThat(plant.next().humidity()).isLessThan(100.0);
    }

    @Test
    void temperatureRandomWalksInSmallSteps() {
        PlantSimulation plant = new PlantSimulation(15.0, 15.0, new Random(SEED));
        double previous = plant.current().temperature();

        for (int i = 0; i < 1_000; i++) {
            double next = plant.next().temperature();
            assertThat(Math.abs(next - previous)).isLessThanOrEqualTo(0.3);
            previous = next;
        }
    }

    @Test
    void temperatureStaysAroundRoomTemperature() {
        PlantSimulation plant = new PlantSimulation(15.0, 15.0, new Random(SEED));

        double max = 0;
        double min = 100;
        for (int i = 0; i < 100_000; i++) {
            double t = plant.next().temperature();
            max = Math.max(max, t);
            min = Math.min(min, t);
        }

        assertThat(min).isGreaterThan(15.0);
        assertThat(max).isLessThan(30.0);
    }

    @Test
    void temperatureRecoversWhenBelowTheMinimum() {
        PlantSimulation plant = new PlantSimulation(30.0, 15.0, new Random(SEED));
        double start = plant.current().temperature();

        IntStream.range(0, 100).forEach(i -> plant.next());

        assertThat(plant.current().temperature()).isGreaterThan(start);
    }

    @Test
    void rejectsAnImpossibleMinimumHumidity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PlantSimulation(15.0, 101.0, new Random(SEED)));
    }
}
