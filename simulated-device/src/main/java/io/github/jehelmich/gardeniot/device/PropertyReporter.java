package io.github.jehelmich.gardeniot.device;

/** Writes one reported property to the device twin. */
@FunctionalInterface
public interface PropertyReporter {

    void report(String name, Object value) throws Exception;
}
