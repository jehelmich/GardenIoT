package io.github.jehelmich.gardeniot.controller;

/** A command to a device failed. */
public class ActuationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ActuationException(String message) {
        super(message);
    }

    public ActuationException(String message, Throwable cause) {
        super(message, cause);
    }
}
