package io.github.jehelmich.gardeniot.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.observability.ObservabilityServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the page, a server-sent event stream of garden changes, the JSON API, and the usual
 * metrics and health endpoints — all on one port, on virtual threads so that idle event
 * streams cost nothing.
 */
final class UiServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UiServer.class);

    private final HttpServer server;

    UiServer(int port, GardenModel model, GardenApi api, Metrics metrics, BooleanSupplier ready) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> serveStatic(exchange));
        server.createContext("/events", exchange -> stream(exchange, model));
        server.createContext("/api/", exchange -> serveApi(exchange, api));
        ObservabilityServer.register(server, metrics, ready);
    }

    void start() {
        server.start();
        log.info("Garden UI on http://0.0.0.0:{}/", server.getAddress().getPort());
    }

    int port() {
        return server.getAddress().getPort();
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String resource = path.equals("/") ? "index.html" : path.substring(1);
        if (resource.contains("..")) {
            respond(exchange, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        try (InputStream in = UiServer.class.getResourceAsStream("/web/" + resource)) {
            if (in == null) {
                respond(exchange, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String type = resource.endsWith(".html")
                    ? "text/html; charset=utf-8"
                    : resource.endsWith(".js")
                            ? "text/javascript; charset=utf-8"
                            : resource.endsWith(".css")
                                    ? "text/css; charset=utf-8"
                                    : resource.endsWith(".svg") ? "image/svg+xml" : "application/octet-stream";
            respond(exchange, 200, type, in.readAllBytes());
        }
    }

    private static void stream(HttpExchange exchange, GardenModel model) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        java.util.concurrent.BlockingQueue<String> queue = new java.util.concurrent.LinkedBlockingQueue<>();
        Consumer<String> listener = queue::offer;
        model.addListener(listener);
        try {
            write(out, model.snapshot());
            while (true) {
                String event = queue.poll(15, java.util.concurrent.TimeUnit.SECONDS);
                if (event == null) {
                    out.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8)); // keeps proxies happy
                    out.flush();
                } else {
                    write(out, event);
                }
            }
        } catch (IOException | InterruptedException e) {
            // client went away
        } finally {
            model.removeListener(listener);
            exchange.close();
        }
    }

    private static void write(OutputStream out, String event) throws IOException {
        out.write(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void serveApi(HttpExchange exchange, GardenApi api) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        GardenApi.Response response =
                api.handle(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body);
        respond(
                exchange,
                response.httpStatus(),
                "application/json",
                response.body().getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
