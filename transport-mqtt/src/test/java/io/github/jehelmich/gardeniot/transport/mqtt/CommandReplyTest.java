package io.github.jehelmich.gardeniot.transport.mqtt;

import io.github.jehelmich.gardeniot.transport.CommandResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommandReplyTest {

    @Test
    void roundTripsAResult() {
        String json = CommandReply.encode(CommandResult.accepted("Started watering"));

        assertThat(json).isEqualTo("{\"status\":202,\"payload\":{\"message\":\"Started watering\"}}");
        CommandResult decoded = CommandReply.decode(json);
        assertThat(decoded.status()).isEqualTo(202);
        assertThat(decoded.isSuccess()).isTrue();
        assertThat(decoded.payload().toString()).contains("Started watering");
    }

    @Test
    void carriesArbitraryPayloads() {
        CommandResult decoded = CommandReply.decode(CommandReply.encode(CommandResult.ok(Map.of("deviceIds", java.util.List.of("a", "b")))));

        assertThat(decoded.payload().toString()).isEqualTo("{\"deviceIds\":[\"a\",\"b\"]}");
    }
}
