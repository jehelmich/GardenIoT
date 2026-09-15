package io.github.jehelmich.gardeniot.device;

import java.util.Random;

/**
 * A toy model of a potted plant standing outside.
 *
 * <p>Soil humidity evaporates a little on every step — faster when it is warm, faster for
 * thirsty species, faster in a drought — and rain puts some back. The pump adds a dose. Air
 * temperature does a small random walk around whatever the weather says.
 *
 * <p>The plant has {@linkplain #health() health} and {@linkplain #growth() growth}: it thrives
 * inside its species' comfortable band, suffers when parched, waterlogged, frozen or baked, and
 * dies when its health reaches zero, after which only a new plant helps.
 *
 * <p>Steps run on the telemetry loop while {@link #water()} arrives from the command thread, so
 * the state is guarded by the instance monitor.
 */
public final class PlantSimulation {

    /** A single sample of the simulated sensors. */
    public record Reading(double temperature, double humidity) {}

    /** Why a plant died. */
    public enum CauseOfDeath {
        THIRST,
        ROOT_ROT,
        FROST,
        HEAT
    }

    static final double INITIAL_HEALTH = 70.0;
    static final double INITIAL_GROWTH = 10.0;
    static final double WATERING_DOSE = 45.0;
    private static final double INITIAL_HUMIDITY = 26.0;
    private static final double MIN_HUMIDITY = 3.0;
    private static final double EVAPORATION_PER_DEGREE = 0.01;
    private static final double TEMPERATURE_STEP = 0.15;
    private static final double TEMPERATURE_PULL = 0.03;
    private static final double HEALTH_GAIN = 0.3;
    private static final double MILD_GAIN = 0.05;
    private static final double PARCHED_LOSS = 0.5;
    private static final double ROT_LOSS = 0.6;
    private static final double FROST_LOSS = 0.4;
    private static final double HEAT_LOSS = 0.3;
    private static final double GROWTH_HALTS_BELOW = 12.0;

    private final PlantProfile profile;
    private final Random random;

    private double temperature = 22.0;
    private double humidity = INITIAL_HUMIDITY;
    private double health = INITIAL_HEALTH;
    private double growth = INITIAL_GROWTH;
    private CauseOfDeath causeOfDeath;

    public PlantSimulation(PlantProfile profile, Random random) {
        this.profile = profile;
        this.random = random;
    }

    public PlantProfile profile() {
        return profile;
    }

    /** Advances the simulation by one step under the given weather and returns the new sensor values. */
    public synchronized Reading next(WeatherConditions weather) {
        stepTemperature(weather);
        stepHumidity(weather);
        stepPlant();
        return new Reading(temperature, humidity);
    }

    /** One pump run: a dose of water, not a flood — though a cactus may disagree. */
    public synchronized void water() {
        humidity = Math.min(100.0, humidity + WATERING_DOSE);
    }

    public synchronized Reading current() {
        return new Reading(temperature, humidity);
    }

    /** 0 (dead) to 100 (thriving). */
    public synchronized double health() {
        return health;
    }

    /** 0 (seedling) to 100 (fully grown); only advances while the plant is comfortable. */
    public synchronized double growth() {
        return growth;
    }

    public synchronized boolean isAlive() {
        return health > 0.0;
    }

    /** Set once the plant has died; {@code null} while it lives. */
    public synchronized CauseOfDeath causeOfDeath() {
        return causeOfDeath;
    }

    private void stepHumidity(WeatherConditions weather) {
        double evaporation =
                temperature * EVAPORATION_PER_DEGREE * profile.transpiration() * weather.evaporationFactor();
        humidity = Math.max(MIN_HUMIDITY, Math.min(100.0, humidity - evaporation + weather.rainPerStep()));
    }

    private void stepTemperature(WeatherConditions weather) {
        double step = (random.nextDouble() * 2.0 - 1.0) * TEMPERATURE_STEP;
        temperature += step + (weather.temperatureTarget() - temperature) * TEMPERATURE_PULL;
    }

    private void stepPlant() {
        if (!isAlive()) {
            return;
        }
        // Temperature stress comes first: a frozen or baked plant does not enjoy its damp soil.
        if (temperature < profile.coldBelow()) {
            lose(FROST_LOSS, CauseOfDeath.FROST);
            return;
        }
        if (temperature > profile.heatAbove()) {
            lose(HEAT_LOSS, CauseOfDeath.HEAT);
            return;
        }
        if (humidity < profile.parchedBelow()) {
            lose(PARCHED_LOSS, CauseOfDeath.THIRST);
        } else if (humidity > profile.waterloggedAbove()) {
            lose(ROT_LOSS, CauseOfDeath.ROOT_ROT);
        } else if (humidity >= profile.minHumidity() && humidity <= profile.maxHumidity()) {
            health = Math.min(100.0, health + HEALTH_GAIN);
            if (temperature >= GROWTH_HALTS_BELOW) {
                growth = Math.min(100.0, growth + profile.growthRate() * health / 100.0);
            }
        } else {
            health = Math.min(100.0, health + MILD_GAIN);
        }
    }

    private void lose(double amount, CauseOfDeath cause) {
        if (!isAlive()) {
            return;
        }
        health = Math.max(0.0, health - amount);
        if (health == 0.0) {
            causeOfDeath = cause;
        }
    }
}
