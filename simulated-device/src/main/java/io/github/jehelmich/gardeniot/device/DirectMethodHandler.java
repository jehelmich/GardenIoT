package io.github.jehelmich.gardeniot.device;

import com.microsoft.azure.sdk.iot.device.twin.DirectMethodPayload;
import com.microsoft.azure.sdk.iot.device.twin.DirectMethodResponse;
import com.microsoft.azure.sdk.iot.device.twin.MethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Answers the direct methods the controller can invoke on the device.
 *
 * <p>Both methods are long-running from the device's point of view (a pump runs, a board
 * restarts), so the handler acknowledges immediately with {@code 202 Accepted}, performs the
 * work on {@code executor}, and records completion as a reported property on the device twin.
 */
public final class DirectMethodHandler implements MethodCallback {

    static final String WATER = "water";
    static final String REBOOT = "reboot";
    static final String LAST_WATER_PROPERTY = "lastWater";
    static final String LAST_REBOOT_PROPERTY = "lastReboot";

    static final int ACCEPTED = 202;
    static final int NOT_FOUND = 404;

    private static final Logger log = LoggerFactory.getLogger(DirectMethodHandler.class);

    private final PlantSimulation plant;
    private final PropertyReporter reporter;
    private final Executor executor;
    private final Duration actionDuration;
    private final Clock clock;

    public DirectMethodHandler(PlantSimulation plant,
                               PropertyReporter reporter,
                               Executor executor,
                               Duration actionDuration,
                               Clock clock) {
        this.plant = plant;
        this.reporter = reporter;
        this.executor = executor;
        this.actionDuration = actionDuration;
        this.clock = clock;
    }

    @Override
    public DirectMethodResponse onMethodInvoked(String methodName, DirectMethodPayload payload, Object context) {
        log.info("Direct method '{}' invoked", methodName);
        return switch (methodName) {
            case WATER -> {
                executor.execute(() -> perform("Watering", plant::water, LAST_WATER_PROPERTY));
                yield accepted("Started watering");
            }
            case REBOOT -> {
                executor.execute(() -> perform("Rebooting", () -> { }, LAST_REBOOT_PROPERTY));
                yield accepted("Started reboot");
            }
            default -> {
                log.warn("Direct method '{}' is not supported", methodName);
                yield new DirectMethodResponse(NOT_FOUND, Map.of("message", "Unknown method " + methodName));
            }
        };
    }

    private static DirectMethodResponse accepted(String message) {
        return new DirectMethodResponse(ACCEPTED, Map.of("message", message));
    }

    private void perform(String description, Runnable action, String completionProperty) {
        try {
            log.info("{}...", description);
            Thread.sleep(actionDuration);
            action.run();
            reporter.report(completionProperty, clock.instant().toString());
            log.info("{} done, twin property {} updated", description, completionProperty);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("{} failed: {}", description, e.getMessage());
        }
    }
}
