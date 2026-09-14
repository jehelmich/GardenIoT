package io.github.jehelmich.gardeniot.config;

import java.util.Arrays;

/** Which transport technology a process uses; selected with the {@code TRANSPORT} variable. */
public enum Transport {
    /** A plain MQTT 5 broker such as Mosquitto: runs anywhere, no account needed. */
    MQTT,
    /** Azure IoT Hub: the real thing. */
    AZURE;

    public static final String VARIABLE = "TRANSPORT";

    public static Transport fromEnvironment(Environment env) {
        String value = env.optional(VARIABLE, MQTT.name());
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    VARIABLE + " must be one of " + Arrays.toString(values()) + ", was '" + value + "'");
        }
    }
}
