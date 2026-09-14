package io.github.jehelmich.gardeniot.transport.mqtt;

import io.github.jehelmich.gardeniot.config.Environment;

import java.time.Duration;
import java.util.Optional;

/**
 * How to reach the MQTT broker.
 *
 * @param host           broker host name
 * @param port           1883 for plain TCP, 8883 for TLS
 * @param tls            whether to connect with TLS using the JVM's trust store
 * @param username       optional broker credentials
 * @param password       optional broker credentials
 * @param topicPrefix    first topic segment, so several installations can share a broker
 * @param commandTimeout how long the cloud side waits for a device to answer a command
 */
public record MqttSettings(String host,
                           int port,
                           boolean tls,
                           Optional<String> username,
                           Optional<String> password,
                           String topicPrefix,
                           Duration commandTimeout) {

    public static final String HOST = "MQTT_HOST";
    public static final String PORT = "MQTT_PORT";
    public static final String TLS = "MQTT_TLS";
    public static final String USERNAME = "MQTT_USERNAME";
    public static final String PASSWORD = "MQTT_PASSWORD";
    public static final String TOPIC_PREFIX = "MQTT_TOPIC_PREFIX";
    public static final String COMMAND_TIMEOUT = "MQTT_COMMAND_TIMEOUT_SECONDS";

    public MqttSettings {
        if (port < 1 || port > 65_535) {
            throw new IllegalStateException(PORT + " must be a TCP port, was " + port);
        }
        if (topicPrefix.isBlank() || topicPrefix.contains("/") || topicPrefix.contains("+") || topicPrefix.contains("#")) {
            throw new IllegalStateException(TOPIC_PREFIX + " must be a single topic segment, was '" + topicPrefix + "'");
        }
    }

    public static MqttSettings fromEnvironment(Environment env) {
        return new MqttSettings(
                env.optional(HOST, "localhost"),
                (int) env.optionalDouble(PORT, 1883),
                Boolean.parseBoolean(env.optional(TLS, "false")),
                Optional.ofNullable(env.optional(USERNAME, null)),
                Optional.ofNullable(env.optional(PASSWORD, null)),
                env.optional(TOPIC_PREFIX, "garden"),
                env.optionalSeconds(COMMAND_TIMEOUT, Duration.ofSeconds(10)));
    }

    public static MqttSettings local(String host, int port) {
        return new MqttSettings(host, port, false, Optional.empty(), Optional.empty(), "garden", Duration.ofSeconds(10));
    }

    public MqttTopics topics() {
        return new MqttTopics(topicPrefix);
    }
}
