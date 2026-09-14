package io.github.jehelmich.gardeniot.controller;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.observability.ObservabilityServer;
import io.github.jehelmich.gardeniot.transport.DeviceCommandSender;
import io.github.jehelmich.gardeniot.transport.TelemetrySource;
import io.github.jehelmich.gardeniot.transport.azure.AzureCommandSender;
import io.github.jehelmich.gardeniot.transport.azure.AzureServiceSettings;
import io.github.jehelmich.gardeniot.transport.azure.AzureTelemetrySource;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttCommandSender;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttSettings;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttTelemetrySource;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the cloud-side controller.
 *
 * <p>Watches every device's telemetry and commands watering when soil gets too dry.
 * Configuration comes from the environment; see {@link ControllerConfig} and the transport
 * settings. Runs until interrupted or until the telemetry stream fails for good.
 */
public final class ControllerApp {

    private static final Logger log = LoggerFactory.getLogger(ControllerApp.class);

    private ControllerApp() {}

    public static void main(String[] args) throws Exception {
        Environment env = Environment.system();
        ControllerConfig config;
        TelemetrySource source;
        DeviceCommandSender commands;
        AutoCloseable commandsResource;
        try {
            config = ControllerConfig.fromEnvironment(env);
            switch (config.transport()) {
                case AZURE -> {
                    AzureServiceSettings azure = AzureServiceSettings.fromEnvironment(env);
                    source = new AzureTelemetrySource(azure);
                    commands = new AzureCommandSender(azure);
                    commandsResource = () -> {};
                }
                case MQTT -> {
                    MqttSettings mqtt = MqttSettings.fromEnvironment(env);
                    source = new MqttTelemetrySource(mqtt);
                    MqttCommandSender sender = new MqttCommandSender(mqtt);
                    commands = sender;
                    commandsResource = sender;
                }
                default -> throw new IllegalStateException("Unsupported transport " + config.transport());
            }
        } catch (IllegalStateException e) {
            log.error("{}", e.getMessage());
            System.exit(2);
            return;
        }

        Metrics metrics = new Metrics();
        AtomicBoolean ready = new AtomicBoolean();
        ObservabilityServer observability = ObservabilityServer.start(env, metrics, ready::get);
        WateringPolicy policy =
                new WateringPolicy(config.humidityThreshold(), config.wateringCooldown(), Clock.systemUTC());
        TelemetryProcessor processor =
                new TelemetryProcessor(policy, new CommandWateringActuator(commands), new ControllerMetrics(metrics));

        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger exitCode = new AtomicInteger(0);
        // Runs exactly once, whether triggered by Ctrl-C or by a failed stream.
        AtomicBoolean closed = new AtomicBoolean();
        Runnable close = () -> {
            if (closed.compareAndSet(false, true)) {
                log.info("Shutting down");
                ready.set(false);
                source.close();
                if (observability != null) {
                    observability.close();
                }
                try {
                    commandsResource.close();
                } catch (Exception e) {
                    log.debug("Closing command sender: {}", e.toString());
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(close, "shutdown"));

        if (commands instanceof MqttCommandSender mqtt) {
            mqtt.start();
        }
        source.start(processor::onTelemetry, error -> {
            log.error("Telemetry stream failed: {}", error.getMessage());
            exitCode.set(1);
            stopped.countDown();
        });
        ready.set(true);
        log.info(
                "Watching telemetry over {}; watering below {}% humidity. Press Ctrl-C to stop.",
                config.transport(), config.humidityThreshold());
        stopped.await();
        close.run();
        // SDK threads are not daemons, so returning from main would not end the process.
        System.exit(exitCode.get());
    }
}
