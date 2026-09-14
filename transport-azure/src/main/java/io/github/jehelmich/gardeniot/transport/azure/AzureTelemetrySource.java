package io.github.jehelmich.gardeniot.transport.azure;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import io.github.jehelmich.gardeniot.transport.TelemetrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reads telemetry from the hub's built-in Event Hub-compatible endpoint.
 *
 * <p>Every partition is read from "now" with a plain consumer client. The controller is
 * stateless and only ever cares about the latest reading, so it has no use for checkpointing;
 * a service that must not miss events would use {@code EventProcessorClient} with a blob
 * checkpoint store instead.
 *
 * <p>The device identity is taken from the {@code iothub-connection-device-id} system property
 * the hub stamps on every message, which a device cannot forge, rather than from the body.
 */
public final class AzureTelemetrySource implements TelemetrySource {

    static final String DEVICE_ID_PROPERTY = "iothub-connection-device-id";

    private static final Logger log = LoggerFactory.getLogger(AzureTelemetrySource.class);

    private final EventHubConsumerAsyncClient consumer;
    private volatile Disposable subscription;

    public AzureTelemetrySource(AzureServiceSettings settings) {
        EventHubClientBuilder builder = new EventHubClientBuilder().consumerGroup(settings.consumerGroup());
        if (settings.usesIdentityForEventHub()) {
            builder.credential(settings.eventHubNamespace().orElseThrow(),
                    settings.eventHubName().orElseThrow(), settings.credential());
        } else {
            builder.connectionString(settings.eventHubConnectionString().orElseThrow());
        }
        this.consumer = builder.buildAsyncConsumerClient();
    }

    @Override
    public void start(Consumer<Telemetry> onTelemetry, Consumer<Throwable> onFailure) {
        log.info("Reading '{}' on consumer group '{}'", consumer.getEventHubName(), consumer.getConsumerGroup());
        subscription = consumer.receive(false).subscribe(
                event -> decode(event.getData()).ifPresent(onTelemetry),
                onFailure::accept);
    }

    static Optional<Telemetry> decode(EventData event) {
        try {
            Telemetry telemetry = TelemetryCodec.fromJson(event.getBodyAsString());
            Object hubDeviceId = event.getSystemProperties().get(DEVICE_ID_PROPERTY);
            if (hubDeviceId != null && !hubDeviceId.toString().equals(telemetry.deviceId())) {
                log.warn("Message from '{}' claims to be from '{}'; trusting the hub",
                        hubDeviceId, telemetry.deviceId());
                telemetry = new Telemetry(hubDeviceId.toString(), telemetry.timestamp(),
                        telemetry.temperature(), telemetry.humidity());
            }
            return Optional.of(telemetry);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring message: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        Disposable current = subscription;
        if (current != null) {
            current.dispose();
        }
        consumer.close();
    }
}
