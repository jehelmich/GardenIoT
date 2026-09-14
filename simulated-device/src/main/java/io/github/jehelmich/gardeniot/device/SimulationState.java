package io.github.jehelmich.gardeniot.device;

import java.time.Instant;

/**
 * The simulator's "god view" of one plant, reported as device state so that an observer can
 * compare what the sensor says with what is really happening in the pot.
 *
 * @param trueHumidity    actual soil humidity, regardless of sensor faults
 * @param trueTemperature actual air temperature
 * @param health          0 (dead) to 100 (thriving)
 * @param growth          0 (seedling) to 100 (fully grown)
 * @param alive           false once health has reached zero
 * @param fault           the current sensor fault
 * @param speed           simulation speed factor; 1 is real time
 * @param waterings       how often the pump has run
 * @param lastWatered     when the pump last ran, or {@code null}
 * @param updatedAt       when this state was reported
 */
public record SimulationState(
        double trueHumidity,
        double trueTemperature,
        double health,
        double growth,
        boolean alive,
        SensorFault fault,
        double speed,
        int waterings,
        Instant lastWatered,
        Instant updatedAt) {

    public static final String NAME = "simulation";
}
