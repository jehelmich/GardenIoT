package io.github.jehelmich.gardeniot.ui;

import io.github.jehelmich.gardeniot.config.Environment;
import io.github.jehelmich.gardeniot.observability.Metrics;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttBusObserver;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttCommandSender;
import io.github.jehelmich.gardeniot.transport.mqtt.MqttSettings;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the garden page: a live view of every plant on the MQTT bus, with controls to
 * water, break sensors, change the simulation speed and add plants. MQTT only — it observes the
 * broker, which IoT Hub does not let a third party do. Runs until interrupted.
 */
public final class GardenUiApp {

    public static final String PORT = "UI_PORT";
    static final int DEFAULT_PORT = 8080;

    private static final Logger log = LoggerFactory.getLogger(GardenUiApp.class);

    private GardenUiApp() {}

    public static void main(String[] args) throws Exception {
        Environment env = Environment.system();
        MqttSettings mqtt;
        int port;
        try {
            mqtt = MqttSettings.fromEnvironment(env);
            port = (int) env.optionalDouble(PORT, DEFAULT_PORT);
        } catch (IllegalStateException e) {
            log.error("{}", e.getMessage());
            System.exit(2);
            return;
        }

        Metrics metrics = new Metrics();
        AtomicBoolean ready = new AtomicBoolean();
        GardenModel model = new GardenModel(Clock.systemUTC());
        MqttBusObserver observer = new MqttBusObserver(mqtt);
        MqttCommandSender commands = new MqttCommandSender(mqtt);
        UiServer server = new UiServer(port, model, new GardenApi(commands, model), metrics, ready::get);

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            log.info("Shutting down");
                            ready.set(false);
                            server.close();
                            observer.close();
                            commands.close();
                            stopped.countDown();
                        },
                        "shutdown"));

        commands.start();
        observer.start(model::apply);
        server.start();
        ready.set(true);
        log.info("Watching the garden on {}:{}. Press Ctrl-C to stop.", mqtt.host(), mqtt.port());
        stopped.await();
    }
}
