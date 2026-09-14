package io.github.jehelmich.gardeniot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.jehelmich.gardeniot.transport.CommandException;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandWateringActuatorTest {

    private final List<String> sent = new ArrayList<>();

    @Test
    void sendsTheWaterCommand() throws CommandException {
        CommandWateringActuator actuator = new CommandWateringActuator((deviceId, command, payload) -> {
            sent.add(deviceId + ":" + command);
            return CommandResult.accepted("Started watering");
        });

        actuator.water("basil");

        assertThat(sent).containsExactly("basil:water");
    }

    @Test
    void acceptsAnySuccessStatus() {
        CommandWateringActuator actuator =
                new CommandWateringActuator((deviceId, command, payload) -> CommandResult.ok(Map.of()));

        assertThatNoException().isThrownBy(() -> actuator.water("basil"));
    }

    @Test
    void reportsARejectedCommand() {
        CommandWateringActuator actuator =
                new CommandWateringActuator((deviceId, command, payload) -> CommandResult.notFound(command));

        assertThatExceptionOfType(CommandException.class)
                .isThrownBy(() -> actuator.water("basil"))
                .withMessageContaining("404");
    }

    @Test
    void propagatesDeliveryFailures() {
        CommandWateringActuator actuator = new CommandWateringActuator((deviceId, command, payload) -> {
            throw new CommandException("no answer");
        });

        assertThatExceptionOfType(CommandException.class)
                .isThrownBy(() -> actuator.water("basil"))
                .withMessage("no answer");
    }
}
