package io.github.jehelmich.gardeniot.transport;

import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import java.util.Optional;

/** Cloud side of device state: the watering profile a device has reported, if any. */
@FunctionalInterface
public interface DeviceProfileSource {

    Optional<WateringProfile> profileOf(String deviceId);

    /** For deployments where devices report nothing: every plant gets the default threshold. */
    DeviceProfileSource NONE = deviceId -> Optional.empty();
}
