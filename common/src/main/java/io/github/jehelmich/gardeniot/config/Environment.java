package io.github.jehelmich.gardeniot.config;

import java.time.Duration;
import java.util.Map;

/**
 * Typed access to configuration passed through environment variables.
 *
 * <p>Connection strings are secrets, so they never live in source files or on the command line;
 * the environment is the lowest common denominator for local shells, containers and CI.
 */
public final class Environment {

    private final Map<String, String> variables;

    public Environment(Map<String, String> variables) {
        this.variables = Map.copyOf(variables);
    }

    public static Environment system() {
        return new Environment(System.getenv());
    }

    /**
     * @throws IllegalStateException if the variable is unset or blank
     */
    public String required(String name) {
        String value = variables.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value.strip();
    }

    public String optional(String name, String defaultValue) {
        String value = variables.get(name);
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    public double optionalDouble(String name, double defaultValue) {
        String value = variables.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be a number, was '" + value + "'", e);
        }
    }

    public Duration optionalSeconds(String name, Duration defaultValue) {
        String value = variables.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long seconds = Long.parseLong(value.strip());
            if (seconds <= 0) {
                throw new IllegalStateException(name + " must be a positive number of seconds, was " + seconds);
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be a whole number of seconds, was '" + value + "'", e);
        }
    }
}
