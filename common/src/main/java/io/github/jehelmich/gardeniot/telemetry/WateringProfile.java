package io.github.jehelmich.gardeniot.telemetry;

/**
 * What a device tells the cloud about its plant's needs, so that the controller can water each
 * plant on its own terms instead of one threshold for the whole garden. Reported as device
 * state — a reported twin property on IoT Hub, a retained topic on MQTT.
 *
 * @param name        the plant, for humans
 * @param minHumidity soil humidity in percent below which the plant wants water
 * @param maxHumidity soil humidity in percent above which it starts to suffer
 */
public record WateringProfile(String name, double minHumidity, double maxHumidity) {

    public static final String STATE_NAME = "profile";

    public WateringProfile {
        if (minHumidity < 0 || maxHumidity > 100 || minHumidity >= maxHumidity) {
            throw new IllegalArgumentException("Need 0 <= min < max <= 100, was " + minHumidity + ".." + maxHumidity);
        }
    }
}
