package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.device.PlantSimulation.Reading;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import io.github.jehelmich.gardeniot.transport.DeviceTransport;
import io.github.jehelmich.gardeniot.transport.DeviceTransportFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One simulated pot with its plant, sensor, pump and connection.
 *
 * <p>The telemetry loop reschedules itself after every reading so that a speed change takes
 * effect on the next tick. Commands come in on the transport's thread and only flip state;
 * anything slow — the pump, a reboot, the technician's drive over — runs on {@code actions}.
 */
public final class VirtualDevice implements AutoCloseable {

    static final String JOB_TECHNICIAN = "technician";
    static final String JOB_REPOT = "repot";

    private static final Logger log = LoggerFactory.getLogger(VirtualDevice.class);
    private static final Duration MIN_INTERVAL = Duration.ofMillis(200);
    private static final int TECHNICIAN_DELAY_FACTOR = 3;
    private static final int REPOT_DELAY_FACTOR = 2;

    private final String deviceId;
    private final Random random;
    private final Duration baseInterval;
    private final Duration baseActionDuration;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Executor actions;
    private final DeviceMetrics metrics;

    private volatile PlantSimulation plant;
    private volatile WeatherProvider weather;
    private DeviceTransport transport;
    private volatile double speed = 1.0;
    private volatile SensorFault fault = SensorFault.NONE;
    private volatile Reading lastReported;
    private volatile int waterings;
    private volatile int repots;
    private volatile Instant lastWatered;
    private volatile String job;
    private volatile Instant jobDoneAt;
    private volatile ScheduledFuture<?> nextTick;
    private volatile boolean closed;

    public VirtualDevice(
            String deviceId,
            PlantProfile profile,
            WeatherProvider weather,
            Random random,
            Duration telemetryInterval,
            Duration actionDuration,
            Clock clock,
            ScheduledExecutorService scheduler,
            Executor actions,
            DeviceMetrics metrics) {
        this.deviceId = deviceId;
        this.random = random;
        this.plant = new PlantSimulation(profile, random);
        this.weather = weather;
        this.baseInterval = telemetryInterval;
        this.baseActionDuration = actionDuration;
        this.clock = clock;
        this.scheduler = scheduler;
        this.actions = actions;
        this.metrics = metrics;
    }

    /** Connects, tells the cloud what the plant needs, and starts publishing. */
    public void start(DeviceTransportFactory transports) throws Exception {
        transport = transports.connect(deviceId, new DeviceCommands(this));
        transport.reportState(WateringProfile.STATE_NAME, plant.profile().wateringProfile());
        log.info("{}: online ({})", deviceId, plant.profile().name());
        scheduleNext(Duration.ZERO);
    }

    public String deviceId() {
        return deviceId;
    }

    PlantSimulation plant() {
        return plant;
    }

    // --- commands, called on the transport's thread ---------------------------------------

    void commandReceived(String command) {
        metrics.command(deviceId, command);
    }

    void startWatering() {
        actions.execute(() -> {
            try {
                log.info("{}: watering...", deviceId);
                Thread.sleep(scaled(baseActionDuration));
                plant.water();
                waterings++;
                lastWatered = clock.instant();
                metrics.watered(deviceId);
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

    /**
     * Sends for the technician, who recalibrates or replaces the sensor.
     *
     * @return false if someone is already on the way
     */
    boolean callTechnician() {
        return startJob(JOB_TECHNICIAN, TECHNICIAN_DELAY_FACTOR, () -> {
            fault = SensorFault.NONE;
            lastReported = null;
            log.info("{}: technician replaced the sensor", deviceId);
        });
    }

    /**
     * Puts a fresh seedling of the same species in the pot.
     *
     * @return false if a job is already under way
     */
    boolean repot() {
        return startJob(JOB_REPOT, REPOT_DELAY_FACTOR, () -> {
            plant = new PlantSimulation(plant.profile(), random);
            repots++;
            log.info("{}: repotted", deviceId);
        });
    }

    private synchronized boolean startJob(String name, int delayFactor, Runnable completion) {
        if (job != null) {
            return false;
        }
        Duration delay = scaled(baseActionDuration.multipliedBy(delayFactor));
        job = name;
        jobDoneAt = clock.instant().plus(delay);
        log.info("{}: {} scheduled, done in {}s", deviceId, name, delay.toSeconds());
        reportState();
        actions.execute(() -> {
            try {
                Thread.sleep(delay);
                completion.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                job = null;
                jobDoneAt = null;
                reportState();
            }
        });
        return true;
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

    void setWeather(WeatherProvider provider) {
        weather = provider;
        log.info("{}: weather now {}", deviceId, provider.current().label());
        reportState();
    }

    public SimulationState state() {
        PlantSimulation current = plant;
        Reading truth = current.current();
        WeatherConditions conditions = weather.current();
        return new SimulationState(
                current.profile().id(),
                current.profile().name(),
                truth.humidity(),
                truth.temperature(),
                current.health(),
                current.growth(),
                current.isAlive(),
                current.causeOfDeath(),
                fault,
                speed,
                conditions.kind(),
                conditions.label(),
                conditions.source(),
                job,
                jobDoneAt,
                waterings,
                repots,
                lastWatered,
                clock.instant());
    }

    // --- the telemetry loop -----------------------------------------------------------------

    /** One tick: advance the plant, read the sensor, publish. Visible for tests. */
    void tick() {
        PlantSimulation current = plant;
        Reading truth = current.next(weather.current());
        Reading reading = fault.apply(truth, lastReported);
        metrics.tick(deviceId, truth, reading, fault, speed, current);
        try {
            if (reading == null) {
                log.info("{}: sensor silent (true humidity {})", deviceId, format(truth.humidity()));
            } else {
                transport.publish(new Telemetry(deviceId, clock.instant(), reading.temperature(), reading.humidity()));
                lastReported = reading;
                log.info(
                        "{}: sent temperature={}°C humidity={}%{}",
                        deviceId,
                        format(reading.temperature()),
                        format(reading.humidity()),
                        fault == SensorFault.NONE
                                ? ""
                                : " (fault " + fault + ", true " + format(truth.humidity()) + "%)");
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
        nextTick = scheduler.schedule(
                () -> {
                    tick();
                    scheduleNext(scaled(baseInterval));
                },
                delay.toMillis(),
                TimeUnit.MILLISECONDS);
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
