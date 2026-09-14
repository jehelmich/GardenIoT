package io.github.jehelmich.gardeniot.transport;

/** A command sender that can also address the fleet as a whole (see {@link FleetChannel}). */
public interface FleetCommandSender extends DeviceCommandSender {

    /**
     * @throws CommandException if no device process picked the command up in time
     */
    CommandResult sendToFleet(String command, Object payload) throws CommandException;
}
