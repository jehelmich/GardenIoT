package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.telemetry.WateringProfile;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a species needs. Values are rough horticultural rules of thumb, not measurements.
 *
 * @param id               short id used in commands and device names
 * @param name             common name
 * @param latinName        botanical name
 * @param minHumidity      wants water below this (what the controller is told)
 * @param maxHumidity      top of the comfortable band
 * @param parchedBelow     loses health below this
 * @param waterloggedAbove loses health above this: root rot
 * @param coldBelow        air temperature in °C below which it takes frost damage
 * @param heatAbove        air temperature in °C above which it takes heat damage
 * @param growthRate       growth per healthy step
 * @param transpiration    how fast it dries its soil, relative to an average plant
 */
public record PlantProfile(
        String id,
        String name,
        String latinName,
        double minHumidity,
        double maxHumidity,
        double parchedBelow,
        double waterloggedAbove,
        double coldBelow,
        double heatAbove,
        double growthRate,
        double transpiration) {

    public static final List<PlantProfile> ALL = List.of(
            new PlantProfile("basil", "Basil", "Ocimum basilicum", 35, 80, 20, 92, 10, 38, 0.06, 1.2),
            new PlantProfile("mint", "Mint", "Mentha spicata", 45, 95, 25, 98, 4, 36, 0.07, 1.4),
            new PlantProfile("tomato", "Tomato", "Solanum lycopersicum", 40, 80, 22, 90, 8, 40, 0.05, 1.3),
            new PlantProfile("lettuce", "Lettuce", "Lactuca sativa", 50, 85, 30, 95, 0, 28, 0.08, 1.3),
            new PlantProfile("fern", "Boston fern", "Nephrolepis exaltata", 55, 90, 35, 97, 10, 32, 0.04, 1.1),
            new PlantProfile("lavender", "Lavender", "Lavandula angustifolia", 12, 45, 6, 65, -5, 42, 0.03, 0.6),
            new PlantProfile("rosemary", "Rosemary", "Salvia rosmarinus", 15, 50, 7, 70, -3, 42, 0.03, 0.7),
            new PlantProfile("cactus", "Prickly pear", "Opuntia ficus-indica", 5, 35, 2, 55, 2, 48, 0.015, 0.3));

    public static final PlantProfile DEFAULT = ALL.get(0);

    public static Optional<PlantProfile> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(profile -> profile.id.equals(wanted)).findFirst();
    }

    /** A device named after a species gets that species; anything else is a basil. */
    public static PlantProfile forDevice(String deviceId) {
        return byId(deviceId).orElse(DEFAULT);
    }

    /** The part the controller needs to know. */
    public WateringProfile wateringProfile() {
        return new WateringProfile(name, minHumidity, maxHumidity);
    }
}
