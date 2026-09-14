package io.github.jehelmich.gardeniot.device;

import java.util.Random;

/**
 * A toy model of a potted plant standing in the sun.
 *
 * <p>Soil humidity evaporates a little on every reading, faster when it is warm, until it
 * bottoms out at {@code minHumidity}. Watering resets it to 100&nbsp;%. Air temperature does a
 * small random walk that is gently pulled back towards room temperature, and drifts up faster
 * when it falls below {@code minTemperature}.
 *
 * <p>The plant itself has {@linkplain #health() health} and {@linkplain #growth() growth}: it
 * thrives while the soil is comfortably damp, suffers when it is parched or waterlogged, and
 * dies when its health reaches zero — after which nothing but a new plant helps.
 *
 * <p>Readings are produced by the telemetry loop while {@link #water()} is invoked from the direct
 * method callback, so the state is guarded by the instance monitor.
 */
public final class PlantSimulation {

    /** A single sample of the simulated sensors. */
    public record Reading(double temperature, double humidity) {}

    private static final double INITIAL_TEMPERATURE = 22.0;
    private static final double INITIAL_HUMIDITY = 26.0;
    private static final double WATERED_HUMIDITY = 100.0;
    private static final double EVAPORATION_PER_DEGREE = 0.01;
    private static final double TEMPERATURE_STEP = 0.15;
    private static final double TEMPERATURE_RECOVERY_STEP = 0.3;
    private static final double TEMPERATURE_REVERSION = 0.02;

    static final double INITIAL_HEALTH = 70.0;
    static final double INITIAL_GROWTH = 10.0;
    static final double COMFORTABLE_HUMIDITY = 30.0;
    static final double WATERLOGGED_HUMIDITY = 97.0;
    static final double PARCHED_HUMIDITY = 20.0;
    private static final double HEALTH_GAIN = 0.3;
    private static final double PARCHED_LOSS = 0.5;
    private static final double WATERLOGGED_LOSS = 0.1;
    private static final double GROWTH_PER_STEP = 0.05;

    private final double minTemperature;
    private final double minHumidity;
    private final Random random;

    private double temperature = INITIAL_TEMPERATURE;
    private double humidity = INITIAL_HUMIDITY;
    private double health = INITIAL_HEALTH;
    private double growth = INITIAL_GROWTH;

    public PlantSimulation(double minTemperature, double minHumidity, Random random) {
        if (minHumidity < 0.0 || minHumidity > WATERED_HUMIDITY) {
            throw new IllegalArgumentException("minHumidity must be within 0..100, was " + minHumidity);
        }
        this.minTemperature = minTemperature;
        this.minHumidity = minHumidity;
        this.random = random;
    }

    /** Advances the simulation by one step and returns the new sensor values. */
    public synchronized Reading next() {
        stepTemperature();
        stepHumidity();
        stepPlant();
        return new Reading(temperature, humidity);
    }

    /** 0 (dead) to 100 (thriving). */
    public synchronized double health() {
        return health;
    }

    /** 0 (seedling) to 100 (fully grown); only advances while the plant is healthy. */
    public synchronized double growth() {
        return growth;
    }

    public synchronized boolean isAlive() {
        return health > 0.0;
    }

    /** Soaks the soil, as the pump would. */
    public synchronized void water() {
        humidity = WATERED_HUMIDITY;
    }

    public synchronized Reading current() {
        return new Reading(temperature, humidity);
    }

    private void stepHumidity() {
        humidity = Math.max(minHumidity, humidity - temperature * EVAPORATION_PER_DEGREE);
    }

    private void stepPlant() {
        if (!isAlive()) {
            return;
        }
        if (humidity < PARCHED_HUMIDITY) {
            health = Math.max(0.0, health - PARCHED_LOSS);
        } else if (humidity > WATERLOGGED_HUMIDITY) {
            health = Math.max(0.0, health - WATERLOGGED_LOSS);
        } else if (humidity >= COMFORTABLE_HUMIDITY) {
            health = Math.min(100.0, health + HEALTH_GAIN);
            growth = Math.min(100.0, growth + GROWTH_PER_STEP * health / 100.0);
        }
    }

    private void stepTemperature() {
        if (temperature < minTemperature) {
            temperature += random.nextDouble() * TEMPERATURE_RECOVERY_STEP;
            return;
        }
        double step = (random.nextDouble() * 2.0 - 1.0) * TEMPERATURE_STEP;
        double reversion = (INITIAL_TEMPERATURE - temperature) * TEMPERATURE_REVERSION;
        temperature += step + reversion;
    }
}
