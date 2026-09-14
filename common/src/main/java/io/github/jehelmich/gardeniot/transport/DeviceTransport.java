package io.github.jehelmich.gardeniot.transport;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;

/**
 * Everything a device needs from its connection to the outside world.
 *
 * <p>One instance per device identity. Commands arrive through the {@link CommandHandler} the
 * transport was {@linkplain DeviceTransportFactory connected} with.
 */
public interface DeviceTransport extends AutoCloseable {

    /** Publishes a reading as a device-to-cloud message. Blocks until the broker has accepted it. */
    void publish(Telemetry telemetry) throws Exception;

    /**
     * Records a piece of device state that outlives the message stream — a reported property on
     * the IoT Hub device twin, a retained message on MQTT. Later values for the same name replace
     * earlier ones.
     *
     * @param value a JSON-serialisable object
     */
    void reportState(String name, Object value) throws Exception;

    @Override
    void close();
}
