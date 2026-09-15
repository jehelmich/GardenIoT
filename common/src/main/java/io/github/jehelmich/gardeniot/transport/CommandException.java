package io.github.jehelmich.gardeniot.transport;

/**
 * A command could not be delivered to a device, was not answered in time, or — when
 * {@link #isRejection()} — was answered with a failure by the device itself.
 */
public class CommandException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean rejection;

    public CommandException(String message) {
        this(message, false);
    }

    public CommandException(String message, boolean rejection) {
        super(message);
        this.rejection = rejection;
    }

    public CommandException(String message, Throwable cause) {
        super(message, cause);
        this.rejection = false;
    }

    /** True if the device received the command and said no; false for delivery problems. */
    public boolean isRejection() {
        return rejection;
    }
}
