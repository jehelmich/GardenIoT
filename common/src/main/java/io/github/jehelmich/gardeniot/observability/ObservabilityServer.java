package io.github.jehelmich.gardeniot.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jehelmich.gardeniot.config.Environment;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tiny HTTP server for the three endpoints an orchestrator wants:
 *
 * <pre>
 * GET /metrics   Prometheus exposition
 * GET /healthz   liveness: 200 while the process runs
 * GET /readyz    readiness: 200 once the application says it is connected
 * </pre>
 *
 * Disabled with {@code METRICS_PORT=0}.
 */
public final class ObservabilityServer implements AutoCloseable {

    public static final String PORT = "METRICS_PORT";
    public static final int DEFAULT_PORT = 8080;

    private static final Logger log = LoggerFactory.getLogger(ObservabilityServer.class);

    private final HttpServer server;

    private ObservabilityServer(HttpServer server) {
        this.server = server;
    }

    /** @return the running server, or {@code null} if disabled */
    public static ObservabilityServer start(Environment env, Metrics metrics, BooleanSupplier ready)
            throws IOException {
        int port = (int) env.optionalDouble(PORT, DEFAULT_PORT);
        if (port <= 0) {
            return null;
        }
        return start(port, metrics, ready);
    }

    public static ObservabilityServer start(int port, Metrics metrics, BooleanSupplier ready) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        register(server, metrics, ready);
        server.setExecutor(null);
        server.start();
        log.info("Metrics on http://0.0.0.0:{}/metrics", server.getAddress().getPort());
        return new ObservabilityServer(server);
    }

    /** Adds the three endpoints to a server the application already runs. */
    public static void register(HttpServer server, Metrics metrics, BooleanSupplier ready) {
        server.createContext(
                "/metrics",
                exchange -> respond(exchange, 200, "text/plain; version=0.0.4; charset=utf-8", metrics.scrape()));
        server.createContext("/healthz", exchange -> respond(exchange, 200, "text/plain", "ok\n"));
        server.createContext("/readyz", exchange -> {
            if (ready.getAsBoolean()) {
                respond(exchange, 200, "text/plain", "ready\n");
            } else {
                respond(exchange, 503, "text/plain", "not ready\n");
            }
        });
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
