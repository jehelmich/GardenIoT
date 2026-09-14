package io.github.jehelmich.gardeniot.transport;

/**
 * Device side of a command: whatever the transport receives (an IoT Hub direct method, an MQTT
 * request) is delivered here by name with its JSON payload, and the result travels back the
 * same way.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * @param command     the command name, for example {@code water}
     * @param payloadJson the request payload as JSON, or {@code null} if there was none
     * @return the response; implementations must not throw
     */
    CommandResult handle(String command, String payloadJson);
}
