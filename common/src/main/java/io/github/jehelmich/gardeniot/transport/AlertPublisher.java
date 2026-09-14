package io.github.jehelmich.gardeniot.transport;

/**
 * Cloud side of "something looks wrong with this device": alerts the controller raises from what
 * it sees in the telemetry, published where operators and the device's own page can find them —
 * a desired twin property on IoT Hub, a retained topic on MQTT.
 */
public interface AlertPublisher {

    void raise(String deviceId, String alert, String message);

    void clear(String deviceId, String alert);

    /** For deployments that only want the logs and the metrics. */
    AlertPublisher NONE = new AlertPublisher() {
        @Override
        public void raise(String deviceId, String alert, String message) {}

        @Override
        public void clear(String deviceId, String alert) {}
    };
}
