package io.github.jehelmich.gardeniot.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservabilityServerTest {

    private final Metrics metrics = new Metrics();
    private final AtomicBoolean ready = new AtomicBoolean();
    private ObservabilityServer server;

    @BeforeEach
    void start() throws IOException {
        server = ObservabilityServer.start(0, metrics, ready::get);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void exposesMetricsInPrometheusFormat() throws Exception {
        metrics.counter("gardeniot_test_total", "a counter").increment();
        metrics.gauge("gardeniot_test_level", "a gauge", "basil", 42.0);

        HttpResponse<String> response = get("/metrics");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/plain");
        assertThat(response.body())
                .contains("gardeniot_test_total 1.0")
                .contains("gardeniot_test_level{device=\"basil\"} 42.0")
                .contains("jvm_memory_used_bytes");
    }

    @Test
    void reportsLivenessAndReadinessSeparately() throws Exception {
        assertThat(get("/healthz").statusCode()).isEqualTo(200);
        assertThat(get("/readyz").statusCode()).isEqualTo(503);

        ready.set(true);

        assertThat(get("/readyz").statusCode()).isEqualTo(200);
    }

    @Test
    void forgetsADevicesGauges() throws Exception {
        metrics.gauge("gardeniot_test_level", "a gauge", "basil", 1.0);
        metrics.gauge("gardeniot_test_level", "a gauge", "mint", 2.0);

        metrics.forget("basil");

        assertThat(get("/metrics").body()).doesNotContain("device=\"basil\"").contains("device=\"mint\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
