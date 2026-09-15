package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.device.PlantSimulation.Reading;

/**
 * Ways the humidity sensor can go wrong. The plant keeps drying out underneath; what changes
 * is what the device reports — and therefore what the controller believes. None of these are
 * detected by the device itself; that is the cloud's job.
 */
public enum SensorFault {

    /** Healthy: the sensor reports the truth. */
    NONE {
        @Override
        Reading apply(Reading truth, Reading previous, long ticksSinceFault) {
            return truth;
        }
    },

    /** The reading froze: the sensor repeats the last value it produced before the fault. */
    STUCK {
        @Override
        Reading apply(Reading truth, Reading previous, long ticksSinceFault) {
            return previous == null ? truth : new Reading(truth.temperature(), previous.humidity());
        }
    },

    /** De-calibrating: the reading creeps upwards a little more every step, so dry soil slowly looks damp. */
    DRIFT {
        @Override
        Reading apply(Reading truth, Reading previous, long ticksSinceFault) {
            return new Reading(
                    truth.temperature(),
                    Math.min(100.0, truth.humidity() + Math.min(45.0, ticksSinceFault * DRIFT_PER_TICK)));
        }
    },

    /** Miscalibrated: humidity is over-reported by a wide margin from the start. */
    OVERREAD {
        @Override
        Reading apply(Reading truth, Reading previous, long ticksSinceFault) {
            return new Reading(truth.temperature(), Math.min(100.0, truth.humidity() + 40.0));
        }
    },

    /** Dead: nothing is reported at all. */
    SILENT {
        @Override
        Reading apply(Reading truth, Reading previous, long ticksSinceFault) {
            return null;
        }
    };

    static final double DRIFT_PER_TICK = 0.15;

    /**
     * @param truth           what the soil is actually like
     * @param previous        the last reading the sensor reported, or {@code null} if none yet
     * @param ticksSinceFault how long the fault has been present, in readings
     * @return what the sensor reports, or {@code null} for no reading
     */
    abstract Reading apply(Reading truth, Reading previous, long ticksSinceFault);
}
