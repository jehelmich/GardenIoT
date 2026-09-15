package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jehelmich.gardeniot.device.PlantSimulation.CauseOfDeath;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlantSimulationTest {

    private static final long SEED = 42L;
    private static final PlantProfile BASIL = PlantProfile.byId("basil").orElseThrow();
    private static final PlantProfile CACTUS = PlantProfile.byId("cactus").orElseThrow();

    private static PlantSimulation plant(PlantProfile profile) {
        return new PlantSimulation(profile, new Random(SEED));
    }

    private static void run(PlantSimulation plant, WeatherConditions weather, int steps) {
        IntStream.range(0, steps).forEach(i -> plant.next(weather));
    }

    @Test
    void soilDriesOutWithEveryReadingAndNeverBelowTheFloor() {
        PlantSimulation plant = plant(BASIL);
        double before = plant.current().humidity();

        assertThat(plant.next(WeatherConditions.CLEAR).humidity()).isLessThan(before);

        run(plant, WeatherConditions.CLEAR, 10_000);
        assertThat(plant.current().humidity()).isEqualTo(3.0);
    }

    @Test
    void thirstyPlantsDryTheirSoilFaster() {
        PlantSimulation mint = plant(PlantProfile.byId("mint").orElseThrow());
        PlantSimulation cactus = plant(CACTUS);

        run(mint, WeatherConditions.CLEAR, 20);
        run(cactus, WeatherConditions.CLEAR, 20);

        assertThat(mint.current().humidity()).isLessThan(cactus.current().humidity());
    }

    @Test
    void wateringAddsADoseRatherThanFlooding() {
        PlantSimulation plant = plant(BASIL);
        run(plant, WeatherConditions.CLEAR, 30);
        double before = plant.current().humidity();

        plant.water();

        assertThat(plant.current().humidity()).isEqualTo(before + PlantSimulation.WATERING_DOSE);
        plant.water();
        plant.water();
        assertThat(plant.current().humidity()).isEqualTo(100.0);
    }

    @Test
    void rainWetsTheSoilAndDroughtDriesIt() {
        PlantSimulation rained = plant(BASIL);
        PlantSimulation parched = plant(BASIL);

        run(rained, WeatherConditions.RAIN, 50);
        run(parched, WeatherConditions.DROUGHT, 50);

        assertThat(rained.current().humidity()).isGreaterThan(26.0);
        assertThat(parched.current().humidity()).isLessThan(rained.current().humidity());
    }

    @Test
    void temperatureFollowsTheWeather() {
        PlantSimulation plant = plant(BASIL);

        run(plant, WeatherConditions.HEATWAVE, 300);
        assertThat(plant.current().temperature()).isBetween(33.0, 39.0);

        run(plant, WeatherConditions.COLD, 300);
        assertThat(plant.current().temperature()).isBetween(0.0, 6.0);
    }

    @Test
    void aComfortablePlantGrowsAndAParchedOneDiesOfThirst() {
        PlantSimulation plant = plant(BASIL);
        plant.water();
        double growthBefore = plant.growth();

        run(plant, WeatherConditions.CLEAR, 150);

        assertThat(plant.health()).isGreaterThan(PlantSimulation.INITIAL_HEALTH);
        assertThat(plant.growth()).isGreaterThan(growthBefore);

        run(plant, WeatherConditions.DROUGHT, 3_000);

        assertThat(plant.isAlive()).isFalse();
        assertThat(plant.causeOfDeath()).isEqualTo(CauseOfDeath.THIRST);
        double growthAtDeath = plant.growth();
        plant.water();
        run(plant, WeatherConditions.CLEAR, 100);
        assertThat(plant.growth()).as("a dead plant does not grow back").isEqualTo(growthAtDeath);
    }

    @Test
    void overwateringKillsByRootRot() {
        PlantSimulation cactus = plant(CACTUS);

        cactus.water();
        cactus.water();
        run(cactus, WeatherConditions.CLEAR, 400);

        assertThat(cactus.isAlive()).isFalse();
        assertThat(cactus.causeOfDeath()).isEqualTo(CauseOfDeath.ROOT_ROT);
    }

    @Test
    void frostKillsATenderPlantButNotAHardyOne() {
        PlantSimulation basil = plant(BASIL);
        PlantSimulation lavender = plant(PlantProfile.byId("lavender").orElseThrow());
        basil.water();
        // Lavender at 26% is comfortable already; watering it would drown it, which is a different test.

        run(basil, WeatherConditions.COLD, 600);
        run(lavender, WeatherConditions.COLD, 600);

        assertThat(basil.isAlive()).isFalse();
        assertThat(basil.causeOfDeath()).isEqualTo(CauseOfDeath.FROST);
        assertThat(lavender.isAlive()).isTrue();
    }

    @Test
    void heatKillsWhatCannotTakeIt() {
        PlantSimulation lettuce = plant(PlantProfile.byId("lettuce").orElseThrow());
        lettuce.water();
        lettuce.water();

        run(lettuce, WeatherConditions.HEATWAVE, 600);

        assertThat(lettuce.isAlive()).isFalse();
        assertThat(lettuce.causeOfDeath()).isEqualTo(CauseOfDeath.HEAT);
    }
}
