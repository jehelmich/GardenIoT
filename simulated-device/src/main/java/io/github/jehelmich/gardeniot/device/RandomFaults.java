package io.github.jehelmich.gardeniot.device;

import java.util.Random;

/**
 * Wear and tear: every so often something breaks. Which thing is drawn from a weighted list —
 * sensors fail in more ways than pumps do — and nothing new breaks while a fault is present or a
 * technician is on the way.
 */
final class RandomFaults {

    /** What can break. */
    enum Breakage {
        SENSOR_STUCK(3),
        SENSOR_DRIFT(3),
        SENSOR_SILENT(1),
        PUMP(2);

        final int weight;

        Breakage(int weight) {
            this.weight = weight;
        }
    }

    private static final int TOTAL_WEIGHT =
            java.util.Arrays.stream(Breakage.values()).mapToInt(b -> b.weight).sum();

    private final Random random;
    private volatile long meanTicksBetweenFaults;

    /**
     * @param meanTicksBetweenFaults average readings between two breakages; 0 turns wear off
     */
    RandomFaults(Random random, long meanTicksBetweenFaults) {
        this.random = random;
        this.meanTicksBetweenFaults = meanTicksBetweenFaults;
    }

    long meanTicksBetweenFaults() {
        return meanTicksBetweenFaults;
    }

    void setMeanTicksBetweenFaults(long ticks) {
        meanTicksBetweenFaults = Math.max(0, ticks);
    }

    /** @return something that just broke, or {@code null} — almost always {@code null} */
    Breakage roll() {
        long mean = meanTicksBetweenFaults;
        if (mean <= 0 || random.nextDouble() >= 1.0 / mean) {
            return null;
        }
        int pick = random.nextInt(TOTAL_WEIGHT);
        for (Breakage breakage : Breakage.values()) {
            pick -= breakage.weight;
            if (pick < 0) {
                return breakage;
            }
        }
        return Breakage.SENSOR_STUCK;
    }
}
