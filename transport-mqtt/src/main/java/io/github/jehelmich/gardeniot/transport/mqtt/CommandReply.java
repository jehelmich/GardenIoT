package io.github.jehelmich.gardeniot.transport.mqtt;

import com.google.gson.JsonElement;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.transport.CommandResult;

/** The JSON body of a command reply: {@code {"status":202,"payload":{...}}}. */
record CommandReply(int status, JsonElement payload) {

    static String encode(CommandResult result) {
        return Json.stringify(new CommandReply(result.status(), Json.gson().toJsonTree(result.payload())));
    }

    static CommandResult decode(String json) {
        CommandReply reply = Json.parse(json, CommandReply.class);
        return new CommandResult(reply.status(), reply.payload());
    }
}
