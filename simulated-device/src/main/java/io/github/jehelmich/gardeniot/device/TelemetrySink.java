package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;

/** Where readings go once they are taken; in production that is IoT Hub. */
@FunctionalInterface
public interface TelemetrySink {

    void publish(Telemetry telemetry) throws Exception;
}
