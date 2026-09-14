package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.observability.ObservabilityServer;
import io.github.jehelmich.gardeniot.transport.DeviceTransportFactory;
import io.github.jehelmich.gardeniot.transport.FleetChannel;
import io.github.jehelmich.gardeniot.transport.azure.AzureDeviceSettings;
import io.github.jehelmich.gardeniot.transport.azure.AzureDeviceTransportFactory;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttDeviceTransportFactory;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point of the device process.
 *
 * <p>Hosts one or more simulated plants, each with its own connection, and — where the transport
 * allows — takes in new plants at runtime. Configuration comes from the environment; see
 * {@link DeviceConfig} and the transport settings. Runs until interrupted.
 */
public final class DeviceApp {

    private static final Logger log = LoggerFactory.getLogger(DeviceApp.class);

    private DeviceApp() {
    }

    public static void main(String[] args) throws Exception {
        Environment env = Environment.system();
        DeviceConfig config;
        DeviceTransportFactory transports;
        List<String> initialIds;
        try {
            config = DeviceConfig.fromEnvironment(env);
            switch (config.transport()) {
                case AZURE -> {
                    AzureDeviceTransportFactory azure = new AzureDeviceTransportFactory(AzureDeviceSettings.fromEnvironment(env));
                    transports = azure;
                    initialIds = config.deviceIds().isEmpty() ? azure.boundDeviceIds() : config.deviceIds();
                }
                case MQTT -> {
                    transports = new MqttDeviceTransportFactory(MqttSettings.fromEnvironment(env));
                    initialIds = config.deviceIds().isEmpty() ? List.of(defaultDeviceId()) : config.deviceIds();
                }
                default -> throw new IllegalStateException("Unsupported transport " + config.transport());
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("{}", e.getMessage());
            System.exit(2);
            return;
        }

        Metrics metrics = new Metrics();
        AtomicBoolean ready = new AtomicBoolean();
        ObservabilityServer observability = ObservabilityServer.start(env, metrics, ready::get);
        DeviceFleet fleet = new DeviceFleet(config, transports, Clock.systemUTC(), new DeviceMetrics(metrics));
        Optional<FleetChannel> fleetChannel = transports.fleetChannel();
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            ready.set(false);
            fleetChannel.ifPresent(FleetChannel::close);
            fleet.close();
            transports.close();
            if (observability != null) {
                observability.close();
            }
            stopped.countDown();
        }, "shutdown"));

        for (String id : initialIds) {
            fleet.add(id);
        }
        if (fleetChannel.isPresent()) {
            fleetChannel.get().subscribe(fleet);
            log.info("Accepting fleet commands");
        }
        ready.set(true);
        log.info("Hosting {} over {}; publishing every {}s. Press Ctrl-C to stop.",
                fleet.deviceIds(), config.transport(), config.telemetryInterval().toSeconds());
        stopped.await();
    }

    /** Without configuration, name the plant after the machine so two processes do not collide. */
    static String defaultDeviceId() {
        String host = DeviceFleet.hostName().toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        return "plant-" + (host.length() > 20 ? host.substring(0, 20) : host);
    }
}
