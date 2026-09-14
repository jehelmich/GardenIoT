package io.github.jehelmich.gardeniot.controller;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodsClient;
import io.github.jehelmich.gardeniot.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entry point of the cloud-side controller.
 *
 * <p>Reads telemetry from the hub's Event Hub-compatible endpoint across all partitions, starting
 * from now, and invokes the {@code water} direct method on any device whose soil is too dry.
 * Configuration comes from the environment; see {@link ControllerConfig}. Runs until interrupted
 * or until the receive link fails for good.
 */
public final class ControllerApp {

    private static final Logger log = LoggerFactory.getLogger(ControllerApp.class);

    private ControllerApp() {
    }

    public static void main(String[] args) throws InterruptedException {
        ControllerConfig config;
        try {
            config = ControllerConfig.fromEnvironment(Environment.system());
        } catch (IllegalStateException e) {
            log.error("{}", e.getMessage());
            System.exit(2);
            return;
        }

        WateringActuator actuator = new DirectMethodWateringActuator(
                new DirectMethodsClient(config.iotHubConnectionString()),
                config.methodResponseTimeout(),
                config.methodConnectTimeout());
        WateringPolicy policy = new WateringPolicy(
                config.humidityThreshold(), config.wateringCooldown(), Clock.systemUTC());
        TelemetryProcessor processor = new TelemetryProcessor(policy, actuator);

        EventHubConsumerAsyncClient consumer = new EventHubClientBuilder()
                .connectionString(config.eventHubConnectionString())
                .consumerGroup(config.consumerGroup())
                .buildAsyncConsumerClient();

        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger exitCode = new AtomicInteger(0);
        Disposable subscription = consumer.receive(false).subscribe(
                event -> processor.onMessage(event.getData().getBodyAsString()),
                error -> {
                    log.error("Receiving failed: {}", error.getMessage());
                    exitCode.set(1);
                    stopped.countDown();
                });

        // Runs exactly once, whether triggered by Ctrl-C or by a failed receive link.
        AtomicBoolean closed = new AtomicBoolean();
        Runnable close = () -> {
            if (closed.compareAndSet(false, true)) {
                log.info("Shutting down");
                subscription.dispose();
                consumer.close();
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(close, "shutdown"));

        log.info("Watching '{}' on consumer group '{}'; watering below {}% humidity. Press Ctrl-C to stop.",
                consumer.getEventHubName(), consumer.getConsumerGroup(), config.humidityThreshold());
        stopped.await();
        close.run();
        // The SDK's reactor threads are not daemons, so returning from main would not end the process.
        System.exit(exitCode.get());
    }
}
