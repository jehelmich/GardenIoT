package io.github.jehelmich.gardeniot.transport;

/**
 * A device's answer to a command, modelled on the direct-method response of IoT Hub: an
 * HTTP-like status plus a small JSON payload.
 *
 * @param status  200-series for success, 404 for an unknown command, 500-series for failure
 * @param payload a JSON-serialisable object, typically a map with a {@code message}
 */
public record CommandResult(int status, Object payload) {

    public static final int OK = 200;
    public static final int ACCEPTED = 202;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int FAILED = 500;

    public static CommandResult accepted(String message) {
        return new CommandResult(ACCEPTED, java.util.Map.of("message", message));
    }

    public static CommandResult ok(Object payload) {
        return new CommandResult(OK, payload);
    }

    public static CommandResult badRequest(String message) {
        return new CommandResult(BAD_REQUEST, java.util.Map.of("message", message));
    }

    public static CommandResult notFound(String command) {
        return new CommandResult(NOT_FOUND, java.util.Map.of("message", "Unknown command " + command));
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
