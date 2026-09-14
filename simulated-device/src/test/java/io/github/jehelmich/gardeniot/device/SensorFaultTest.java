package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jehelmich.gardeniot.device.PlantSimulation.Reading;
import org.junit.jupiter.api.Test;

class SensorFaultTest {

    private static final Reading TRUTH = new Reading(22.0, 20.0);
    private static final Reading PREVIOUS = new Reading(21.0, 55.0);

    @Test
    void healthySensorReportsTheTruth() {
        assertThat(SensorFault.NONE.apply(TRUTH, PREVIOUS)).isEqualTo(TRUTH);
    }

    @Test
    void stuckSensorRepeatsThePreviousHumidity() {
        assertThat(SensorFault.STUCK.apply(TRUTH, PREVIOUS)).isEqualTo(new Reading(22.0, 55.0));
        assertThat(SensorFault.STUCK.apply(TRUTH, null)).isEqualTo(TRUTH);
    }

    @Test
    void overreadingSensorAddsAMarginButStaysWithinRange() {
        assertThat(SensorFault.OVERREAD.apply(TRUTH, PREVIOUS).humidity()).isEqualTo(60.0);
        assertThat(SensorFault.OVERREAD.apply(new Reading(22.0, 90.0), null).humidity())
                .isEqualTo(100.0);
    }

    @Test
    void silentSensorReportsNothing() {
        assertThat(SensorFault.SILENT.apply(TRUTH, PREVIOUS)).isNull();
    }
}
