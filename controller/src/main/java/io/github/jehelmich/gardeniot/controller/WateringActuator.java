package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.transport.CommandException;

/** Makes a device water its plant. */
@FunctionalInterface
public interface WateringActuator {

    /**
     * @throws CommandException if the command could not be delivered or the device rejected it
     */
    void water(String deviceId) throws CommandException;
}
