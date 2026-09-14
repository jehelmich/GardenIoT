package io.github.jehelmich.gardeniot.device;

import java.util.Random;

/**
 * A toy model of a potted plant standing in the sun.
 *
 * <p>Soil humidity evaporates a little on every reading, faster when it is warm, until it
 * bottoms out at {@code minHumidity}. Watering resets it to 100&nbsp;%. Air temperature does a
 * small random walk and drifts back up when it falls below {@code minTemperature}.
 *
 * <p>Readings are produced by the telemetry loop while {@link #water()} is invoked from the direct
 * method callback, so the state is guarded by the instance monitor.
 */
public final class PlantSimulation {

    /** A single sample of the simulated sensors. */
    public record Reading(double temperature, double humidity) {
    }

    private static final double INITIAL_TEMPERATURE = 22.0;
    private static final double INITIAL_HUMIDITY = 26.0;
    private static final double WATERED_HUMIDITY = 100.0;
    private static final double EVAPORATION_PER_DEGREE = 0.01;
    private static final double TEMPERATURE_STEP = 0.15;
    private static final double TEMPERATURE_RECOVERY_STEP = 0.3;

    private final double minTemperature;
    private final double minHumidity;
    private final Random random;

    private double temperature = INITIAL_TEMPERATURE;
    private double humidity = INITIAL_HUMIDITY;

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
        return new Reading(temperature, humidity);
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

    private void stepTemperature() {
        if (temperature < minTemperature) {
            temperature += random.nextDouble() * TEMPERATURE_RECOVERY_STEP;
            return;
        }
        double r = random.nextDouble();
        temperature += (r >= 0.5 ? r : -r) * TEMPERATURE_STEP;
    }
}
