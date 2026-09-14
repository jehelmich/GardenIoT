package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.CommandException;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.DeviceCommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Waters a device by sending it the {@code water} command over whatever transport is in use. */
public final class CommandWateringActuator implements WateringActuator {

    static final String COMMAND = "water";

    private static final Logger log = LoggerFactory.getLogger(CommandWateringActuator.class);

    private final DeviceCommandSender commands;

    public CommandWateringActuator(DeviceCommandSender commands) {
        this.commands = commands;
    }

    @Override
    public void water(String deviceId) throws CommandException {
        log.info("{}: sending '{}'", deviceId, COMMAND);
        CommandResult result = commands.send(deviceId, COMMAND, null);
        if (!result.isSuccess()) {
            throw new CommandException("Device " + deviceId + " answered '" + COMMAND + "' with " + result.status()
                    + ": " + Json.stringify(result.payload()));
        }
        log.info("{}: answered {} {}", deviceId, result.status(), Json.stringify(result.payload()));
    }
}
