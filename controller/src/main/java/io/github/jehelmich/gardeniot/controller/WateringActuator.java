package io.github.jehelmich.gardeniot.controller;

/** Makes a device water its plant. */
@FunctionalInterface
public interface WateringActuator {

    /**
     * @throws ActuationException if the command could not be delivered or the device rejected it
     */
    void water(String deviceId);
}
