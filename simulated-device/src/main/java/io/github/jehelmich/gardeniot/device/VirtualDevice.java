package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.device.PlantSimulation.Reading;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.transport.DeviceTransport;
import io.github.jehelmich.gardeniot.transport.DeviceTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One simulated plant with its sensor, pump and connection.
 *
 * <p>The telemetry loop reschedules itself after every reading so that a speed change takes
 * effect on the next tick. Commands come in on the transport's thread and only flip state;
 * anything slow (the pump, a reboot) runs on {@code actions}.
 */
public final class VirtualDevice implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VirtualDevice.class);
    private static final Duration MIN_INTERVAL = Duration.ofMillis(200);

    private final String deviceId;
    private final PlantSimulation plant;
    private final Duration baseInterval;
    private final Duration baseActionDuration;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Executor actions;

    private DeviceTransport transport;
    private volatile double speed = 1.0;
    private volatile SensorFault fault = SensorFault.NONE;
    private volatile Reading lastReported;
    private volatile int waterings;
    private volatile Instant lastWatered;
    private volatile ScheduledFuture<?> nextTick;
    private volatile boolean closed;

    public VirtualDevice(String deviceId,
                         PlantSimulation plant,
                         Duration telemetryInterval,
                         Duration actionDuration,
                         Clock clock,
                         ScheduledExecutorService scheduler,
                         Executor actions) {
        this.deviceId = deviceId;
        this.plant = plant;
        this.baseInterval = telemetryInterval;
        this.baseActionDuration = actionDuration;
        this.clock = clock;
        this.scheduler = scheduler;
        this.actions = actions;
    }

    /** Connects and starts publishing. */
    public void start(DeviceTransportFactory transports) throws Exception {
        transport = transports.connect(deviceId, new DeviceCommands(this));
        log.info("{}: online", deviceId);
        scheduleNext(Duration.ZERO);
    }

    public String deviceId() {
        return deviceId;
    }

    // --- commands, called on the transport's thread ---------------------------------------

    void startWatering() {
        actions.execute(() -> {
            try {
                log.info("{}: watering...", deviceId);
                Thread.sleep(scaled(baseActionDuration));
                plant.water();
                waterings++;
                lastWatered = clock.instant();
                log.info("{}: watered", deviceId);
                reportState();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    void startReboot() {
        actions.execute(() -> {
            try {
                log.info("{}: rebooting...", deviceId);
                Thread.sleep(scaled(baseActionDuration));
                log.info("{}: rebooted", deviceId);
                report("lastReboot", clock.instant());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    void setSpeed(double factor) {
        speed = factor;
        log.info("{}: speed {}x", deviceId, factor);
    }

    void setFault(SensorFault newFault) {
        fault = newFault;
        log.info("{}: sensor fault {}", deviceId, newFault);
        reportState();
    }

    public SimulationState state() {
        Reading truth = plant.current();
        return new SimulationState(truth.humidity(), truth.temperature(), fault, speed,
                waterings, lastWatered, clock.instant());
    }

    // --- the telemetry loop -----------------------------------------------------------------

    /** One tick: advance the plant, read the sensor, publish. Visible for tests. */
    void tick() {
        Reading truth = plant.next();
        Reading reading = fault.apply(truth, lastReported);
        try {
            if (reading == null) {
                log.info("{}: sensor silent (true humidity {})", deviceId, format(truth.humidity()));
            } else {
                transport.publish(new Telemetry(deviceId, clock.instant(), reading.temperature(), reading.humidity()));
                lastReported = reading;
                log.info("{}: sent temperature={}°C humidity={}%{}", deviceId,
                        format(reading.temperature()), format(reading.humidity()),
                        fault == SensorFault.NONE ? "" : " (fault " + fault + ", true " + format(truth.humidity()) + "%)");
            }
            transport.reportState(SimulationState.NAME, state());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("{}: failed to send: {}", deviceId, e.getMessage());
        }
    }

    private void scheduleNext(Duration delay) {
        if (closed) {
            return;
        }
        nextTick = scheduler.schedule(() -> {
            tick();
            scheduleNext(scaled(baseInterval));
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Duration scaled(Duration base) {
        Duration scaled = Duration.ofMillis((long) (base.toMillis() / speed));
        return scaled.compareTo(MIN_INTERVAL) < 0 ? MIN_INTERVAL : scaled;
    }

    private void reportState() {
        report(SimulationState.NAME, state());
    }

    private void report(String name, Object value) {
        try {
            transport.reportState(name, value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("{}: failed to report {}: {}", deviceId, name, e.getMessage());
        }
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }

    @Override
    public void close() {
        closed = true;
        ScheduledFuture<?> pending = nextTick;
        if (pending != null) {
            pending.cancel(false);
        }
        if (transport != null) {
            transport.close();
        }
        log.info("{}: offline", deviceId);
    }
}
