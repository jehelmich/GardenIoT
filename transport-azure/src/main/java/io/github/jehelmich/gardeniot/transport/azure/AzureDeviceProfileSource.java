package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.twin.Twin;
import com.microsoft.azure.sdk.iot.service.twin.TwinClient;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import io.github.jehelmich.gardeniot.transport.DeviceProfileSource;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watering profiles from the {@code profile} reported property of each device twin, fetched on
 * first sight of a device and refreshed every few minutes. A device that reports no profile is
 * remembered as such so the hub is not asked on every reading.
 */
public final class AzureDeviceProfileSource implements DeviceProfileSource {

    static final Duration REFRESH = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(AzureDeviceProfileSource.class);

    private record Entry(Optional<WateringProfile> profile, Instant fetchedAt) {}

    private final TwinClient twins;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public AzureDeviceProfileSource(AzureServiceSettings settings) {
        this(
                settings.usesIdentityForHub()
                        ? new TwinClient(settings.hubHostName().orElseThrow(), settings.credential())
                        : new TwinClient(settings.serviceConnectionString().orElseThrow()),
                Clock.systemUTC());
    }

    AzureDeviceProfileSource(TwinClient twins, Clock clock) {
        this.twins = twins;
        this.clock = clock;
    }

    @Override
    public Optional<WateringProfile> profileOf(String deviceId) {
        Instant now = clock.instant();
        Entry entry = cache.get(deviceId);
        if (entry == null || Duration.between(entry.fetchedAt(), now).compareTo(REFRESH) >= 0) {
            entry = new Entry(fetch(deviceId), now);
            cache.put(deviceId, entry);
        }
        return entry.profile();
    }

    private Optional<WateringProfile> fetch(String deviceId) {
        try {
            Twin twin = twins.get(deviceId);
            Object reported = twin.getReportedProperties().get(WateringProfile.STATE_NAME);
            if (reported == null) {
                return Optional.empty();
            }
            return Optional.of(parse(reported));
        } catch (IotHubException | IOException | RuntimeException e) {
            log.warn("{}: could not read the twin: {}", deviceId, e.getMessage());
            return Optional.empty();
        }
    }

    static WateringProfile parse(Object reported) {
        // The SDK hands nested properties back as a Map; go through JSON to reuse the record's checks.
        return Json.parse(Json.stringify(reported), WateringProfile.class);
    }
}
