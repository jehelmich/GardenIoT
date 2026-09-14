package io.github.jehelmich.gardeniot.transport;

/** Cloud side of a command: invoke something on a device and wait for its answer. */
@FunctionalInterface
public interface DeviceCommandSender {

    /**
     * @param payload a JSON-serialisable request payload, or {@code null}
     * @throws CommandException if the command could not be delivered or timed out
     */
    CommandResult send(String deviceId, String command, Object payload) throws CommandException;
}
