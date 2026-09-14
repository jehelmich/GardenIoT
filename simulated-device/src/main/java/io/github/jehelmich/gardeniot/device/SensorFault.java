package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.device.PlantSimulation.Reading;

/**
 * Ways the humidity sensor can go wrong. The plant keeps drying out underneath; what changes
 * is what the device reports — and therefore what the controller believes.
 */
public enum SensorFault {

    /** Healthy: the sensor reports the truth. */
    NONE {
        @Override
        Reading apply(Reading truth, Reading previous) {
            return truth;
        }
    },

    /** The reading froze: the sensor repeats the last value it produced before the fault. */
    STUCK {
        @Override
        Reading apply(Reading truth, Reading previous) {
            return previous == null ? truth : new Reading(truth.temperature(), previous.humidity());
        }
    },

    /** Miscalibrated: humidity is over-reported by a wide margin, so dry soil looks damp. */
    OVERREAD {
        @Override
        Reading apply(Reading truth, Reading previous) {
            return new Reading(truth.temperature(), Math.min(100.0, truth.humidity() + 40.0));
        }
    },

    /** Dead: nothing is reported at all. */
    SILENT {
        @Override
        Reading apply(Reading truth, Reading previous) {
            return null;
        }
    };

    /**
     * @param truth    what the soil is actually like
     * @param previous the last reading the sensor reported, or {@code null} if none yet
     * @return what the sensor reports, or {@code null} for no reading
     */
    abstract Reading apply(Reading truth, Reading previous);
}
