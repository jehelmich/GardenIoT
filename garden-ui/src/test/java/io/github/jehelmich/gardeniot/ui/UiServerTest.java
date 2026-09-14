package io.github.jehelmich.gardeniot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.transport.CommandException;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.FleetCommandSender;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver.BusMessage;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver.Kind;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UiServerTest {

    /** Remembers what was sent and answers like a well-behaved device. */
    private static final class FakeSender implements FleetCommandSender {
        final List<String> sent = new ArrayList<>();

        @Override
        public CommandResult send(String deviceId, String command, Object payload) throws CommandException {
            sent.add(deviceId + ":" + command + (payload == null ? "" : ":" + payload));
            if (deviceId.equals("ghost")) {
                throw new CommandException("No answer");
            }
            return command.equals("water") ? CommandResult.accepted("Started watering") : CommandResult.ok(Map.of());
        }

        @Override
        public CommandResult sendToFleet(String command, Object payload) {
            sent.add("fleet:" + command + ":" + payload);
            return command.equals("addPlant")
                    ? CommandResult.ok(Map.of("deviceId", "thyme"))
                    : CommandResult.ok(Map.of());
        }
    }

    private final FakeSender sender = new FakeSender();
    private final GardenModel model = new GardenModel(Clock.systemUTC());
    private final HttpClient http = HttpClient.newHttpClient();
    private UiServer server;

    @BeforeEach
    void start() throws Exception {
        server = new UiServer(0, model, new GardenApi(sender, model), new Metrics(), () -> true);
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void servesThePageAndTheHealthEndpoints() throws Exception {
        HttpResponse<String> page = get("/");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
        assertThat(page.body()).contains("<title>GardenIoT</title>").contains("EventSource('/events')");
        assertThat(get("/healthz").statusCode()).isEqualTo(200);
        assertThat(get("/readyz").statusCode()).isEqualTo(200);
        assertThat(get("/metrics").body()).contains("jvm_memory_used_bytes");
        assertThat(get("/../etc/passwd").statusCode()).isEqualTo(404);
    }

    @Test
    void streamsASnapshotThenLiveEvents() throws Exception {
        model.apply(new BusMessage(Kind.STATUS, "basil", null, "online"));

        HttpResponse<java.io.InputStream> stream =
                http.send(HttpRequest.newBuilder(uri("/events")).build(), HttpResponse.BodyHandlers.ofInputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream.body()));

        assertThat(stream.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        String snapshot = nextData(reader);
        assertThat(snapshot).contains("\"type\":\"snapshot\"").contains("basil");

        model.apply(new BusMessage(Kind.COMMAND, "basil", "water", null));

        assertThat(nextData(reader)).contains("\"type\":\"command\"").contains("\"command\":\"water\"");
        stream.body().close();
    }

    @Test
    void translatesTheApiIntoCommands() throws Exception {
        assertThat(post("/api/plants/basil/commands/water", null).statusCode()).isEqualTo(200);
        assertThat(post("/api/plants/basil/commands/fault", "{\"type\":\"STUCK\"}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(post("/api/plants", "{\"deviceId\":\"thyme\"}").statusCode()).isEqualTo(200);
        assertThat(delete("/api/plants/thyme").statusCode()).isEqualTo(200);

        assertThat(sender.sent)
                .containsExactly(
                        "basil:water",
                        "basil:fault:{\"type\":\"STUCK\"}",
                        "fleet:addPlant:{deviceId=thyme}",
                        "fleet:removePlant:{deviceId=thyme}");
    }

    @Test
    void refusesWhatItShouldNotForward() throws Exception {
        assertThat(post("/api/plants/basil/commands/selfDestruct", null).statusCode())
                .isEqualTo(400);
        assertThat(post("/api/plants", "{\"deviceId\":\"no spaces\"}").statusCode())
                .isEqualTo(400);
        assertThat(post("/api/plants", "{}").statusCode()).isEqualTo(400);
        assertThat(post("/api/speed", "{}").statusCode()).isEqualTo(400);
        assertThat(post("/api/nothing", null).statusCode()).isEqualTo(404);
        assertThat(post("/api/plants/ghost/commands/water", null).statusCode()).isEqualTo(504);
        assertThat(sender.sent).containsExactly("ghost:water");
    }

    @Test
    void appliesSpeedToEveryKnownPlant() throws Exception {
        model.apply(new BusMessage(Kind.STATUS, "basil", null, "online"));
        model.apply(new BusMessage(Kind.STATUS, "mint", null, "online"));

        HttpResponse<String> response = post("/api/speed", "{\"factor\":25}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sender.sent).containsExactly("basil:setSpeed:{\"factor\":25}", "mint:setSpeed:{\"factor\":25}");
    }

    private static String nextData(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                return line.substring(6);
            }
        }
        throw new AssertionError("stream ended");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(
                HttpRequest.newBuilder(uri(path))
                        .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }
}
