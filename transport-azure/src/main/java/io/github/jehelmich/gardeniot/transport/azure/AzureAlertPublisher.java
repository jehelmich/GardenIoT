package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.twin.Twin;
import com.microsoft.azure.sdk.iot.service.twin.TwinClient;
import io.github.jehelmich.gardeniot.transport.AlertPublisher;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alerts as the {@code alerts} desired property of the device twin — the cloud's side of the
 * twin, which the device (and anyone reading the twin) sees. Clearing sets the entry to null,
 * which removes it from the twin.
 */
public final class AzureAlertPublisher implements AlertPublisher {

    static final String PROPERTY = "alerts";
    private static final Logger log = LoggerFactory.getLogger(AzureAlertPublisher.class);

    private final TwinClient twins;
    private final Clock clock;

    public AzureAlertPublisher(AzureServiceSettings settings) {
        this(
                settings.usesIdentityForHub()
                        ? new TwinClient(settings.hubHostName().orElseThrow(), settings.credential())
                        : new TwinClient(settings.serviceConnectionString().orElseThrow()),
                Clock.systemUTC());
    }

    AzureAlertPublisher(TwinClient twins, Clock clock) {
        this.twins = twins;
        this.clock = clock;
    }

    @Override
    public void raise(String deviceId, String alert, String message) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("message", message);
        entry.put("raisedAt", clock.instant().toString());
        patch(deviceId, alert, entry);
    }

    @Override
    public void clear(String deviceId, String alert) {
        patch(deviceId, alert, null);
    }

    /** A patch touching only {@code desired.alerts.<alert>}; visible for tests. */
    static Twin patchFor(String deviceId, String alert, Object value) {
        Twin twin = new Twin(deviceId);
        Map<String, Object> alerts = new HashMap<>();
        alerts.put(alert, value);
        twin.getDesiredProperties().put(PROPERTY, alerts);
        return twin;
    }

    private void patch(String deviceId, String alert, Object value) {
        try {
            twins.patch(patchFor(deviceId, alert, value));
        } catch (IotHubException | IOException | RuntimeException e) {
            log.warn("{}: could not update alerts on the twin: {}", deviceId, e.getMessage());
        }
    }
}
