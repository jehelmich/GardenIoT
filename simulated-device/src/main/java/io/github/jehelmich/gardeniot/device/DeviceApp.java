package io.github.jehelmich.gardeniot.device;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.twin.TwinCollection;
import io.github.jehelmich.gardeniot.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point of the simulated garden device.
 *
 * <p>Connects to IoT Hub over MQTT, publishes a reading every few seconds, and listens for the
 * {@code water} and {@code reboot} direct methods. Configuration comes from the environment; see
 * {@link DeviceConfig}. Runs until interrupted.
 */
public final class DeviceApp {

    private static final Logger log = LoggerFactory.getLogger(DeviceApp.class);

    private static final double MIN_TEMPERATURE = 15.0;
    private static final double MIN_HUMIDITY = 15.0;

    private DeviceApp() {
    }

    public static void main(String[] args) throws Exception {
        DeviceConfig config;
        try {
            config = DeviceConfig.fromEnvironment(Environment.system());
        } catch (IllegalStateException e) {
            log.error("{}", e.getMessage());
            System.exit(2);
            return;
        }
        Clock clock = Clock.systemUTC();
        PlantSimulation plant = new PlantSimulation(MIN_TEMPERATURE, MIN_HUMIDITY, new Random());

        DeviceClient client = new DeviceClient(config.connectionString(), IotHubClientProtocol.MQTT);
        client.setConnectionStatusChangeCallback(
                change -> log.info("Connection {} ({})", change.getNewStatus(), change.getNewStatusReason()),
                null);

        ExecutorService actions = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch stopped = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            scheduler.shutdownNow();
            actions.shutdownNow();
            client.close();
            stopped.countDown();
        }, "shutdown"));

        log.info("Connecting device '{}'", config.deviceId());
        client.open(true);

        PropertyReporter reporter = (name, value) ->
                client.updateReportedProperties(new TwinCollection(Map.of(name, value)));
        client.subscribeToMethods(
                new DirectMethodHandler(plant, reporter, actions, config.actionDuration(), clock), null);

        TelemetrySink sink = new IotHubTelemetrySink(client);
        scheduler.scheduleWithFixedDelay(
                new TelemetryPublisher(config.deviceId(), plant, clock, sink),
                0, config.telemetryInterval().toMillis(), TimeUnit.MILLISECONDS);

        log.info("Publishing every {}s. Press Ctrl-C to stop.", config.telemetryInterval().toSeconds());
        stopped.await();
    }
}
