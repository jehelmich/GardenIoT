package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.device.PlantSimulation.CauseOfDeath;
import java.time.Instant;

/**
 * The simulator's "god view" of one plant, reported as device state so that an observer can
 * compare what the sensor says with what is really happening in the pot.
 *
 * @param profile         id of the species in the pot
 * @param profileName     its common name
 * @param trueHumidity    actual soil humidity, regardless of sensor faults
 * @param trueTemperature actual air temperature
 * @param health          0 (dead) to 100 (thriving)
 * @param growth          0 (seedling) to 100 (fully grown)
 * @param alive           false once health has reached zero
 * @param causeOfDeath    why, once dead
 * @param fault           the current sensor fault
 * @param pump            "OK", or "FAILED" when the pump no longer delivers water
 * @param wearMeanTicks   average readings between random breakages; 0 when wear is off
 * @param speed           simulation speed factor; 1 is real time
 * @param weather         headline weather
 * @param weatherLabel    weather as text
 * @param weatherSource   clear, auto, or the place live weather comes from
 * @param job             maintenance under way: "technician" or "repot", else null
 * @param jobDoneAt       when that job completes
 * @param waterings       how often the pump has run on this plant
 * @param repots          how many plants have stood in this pot before the current one
 * @param lastWatered     when the pump last ran, or null
 * @param updatedAt       when this state was reported
 */
public record SimulationState(
        String profile,
        String profileName,
        double trueHumidity,
        double trueTemperature,
        double health,
        double growth,
        boolean alive,
        CauseOfDeath causeOfDeath,
        SensorFault fault,
        String pump,
        long wearMeanTicks,
        double speed,
        WeatherConditions.Kind weather,
        String weatherLabel,
        String weatherSource,
        String job,
        Instant jobDoneAt,
        int waterings,
        int repots,
        Instant lastWatered,
        Instant updatedAt) {

    public static final String NAME = "simulation";
}
