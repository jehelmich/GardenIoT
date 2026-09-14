package io.github.jehelmich.gardeniot.transport;

/** A command could not be delivered to a device or was not answered in time. */
public class CommandException extends Exception {

    private static final long serialVersionUID = 1L;

    public CommandException(String message) {
        super(message);
    }

    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
