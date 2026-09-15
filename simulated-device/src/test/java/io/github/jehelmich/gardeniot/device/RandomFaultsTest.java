package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomFaultsTest {

    @Test
    void breaksNothingWhenOff() {
        RandomFaults wear = new RandomFaults(new Random(1L), 0);

        assertThat(IntStream.range(0, 10_000).mapToObj(i -> wear.roll()).filter(b -> b != null))
                .isEmpty();
    }

    @Test
    void breaksAtRoughlyTheConfiguredRateAcrossEveryKind() {
        RandomFaults wear = new RandomFaults(new Random(1L), 100);
        Map<RandomFaults.Breakage, Integer> counts = new EnumMap<>(RandomFaults.Breakage.class);

        IntStream.range(0, 100_000)
                .mapToObj(i -> wear.roll())
                .filter(b -> b != null)
                .forEach(b -> counts.merge(b, 1, Integer::sum));

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isBetween(800, 1200);
        assertThat(counts).containsKeys(RandomFaults.Breakage.values());
        assertThat(counts.get(RandomFaults.Breakage.SENSOR_STUCK))
                .isGreaterThan(counts.get(RandomFaults.Breakage.SENSOR_SILENT));
    }

    @Test
    void theRateCanBeChangedLater() {
        RandomFaults wear = new RandomFaults(new Random(1L), 0);
        wear.setMeanTicksBetweenFaults(10);

        assertThat(IntStream.range(0, 1_000).mapToObj(i -> wear.roll()).anyMatch(b -> b != null))
                .isTrue();
        wear.setMeanTicksBetweenFaults(-5);
        assertThat(wear.meanTicksBetweenFaults()).isZero();
    }
}
