package io.github.jehelmich.gardeniot.transport;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;

import java.util.function.Consumer;

/** Cloud side of the telemetry stream: readings from every device, as they arrive. */
public interface TelemetrySource extends AutoCloseable {

    /**
     * Starts delivering readings. Readings that cannot be decoded are logged and dropped by the
     * transport; {@code onFailure} is called once if the stream fails for good.
     */
    void start(Consumer<Telemetry> onTelemetry, Consumer<Throwable> onFailure) throws Exception;

    @Override
    void close();
}
