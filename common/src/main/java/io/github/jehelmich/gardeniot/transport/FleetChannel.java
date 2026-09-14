package io.github.jehelmich.gardeniot.transport;

/**
 * Commands addressed to "whichever device process is listening" rather than to a device.
 *
 * <p>Each command is delivered to exactly one subscriber, so a fleet of device processes can
 * share the work of, say, hosting newly added plants.
 */
public interface FleetChannel extends AutoCloseable {

    void subscribe(CommandHandler handler) throws Exception;

    @Override
    void close();
}
