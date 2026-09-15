package io.github.jehelmich.gardeniot.device;

/**
 * What the weather does to a pot, per simulation step.
 *
 * @param kind              the headline, for display
 * @param label             a human description, e.g. "Rain, 14 °C"
 * @param source            where it came from: "clear", "auto", or a place name for live weather
 * @param temperatureTarget air temperature the pot drifts towards, in °C
 * @param rainPerStep       soil humidity gained per step from rain
 * @param evaporationFactor multiplier on the soil's drying rate; sun and wind raise it
 */
public record WeatherConditions(
        Kind kind,
        String label,
        String source,
        double temperatureTarget,
        double rainPerStep,
        double evaporationFactor) {

    /** Headline weather, as the page draws it. */
    public enum Kind {
        CLEAR,
        CLOUDY,
        FOG,
        RAIN,
        STORM,
        SNOW,
        DROUGHT,
        HEATWAVE,
        COLD
    }

    static final WeatherConditions CLEAR = new WeatherConditions(Kind.CLEAR, "Clear, 22 °C", "clear", 22, 0, 1.0);
    static final WeatherConditions RAIN = new WeatherConditions(Kind.RAIN, "Rain, 15 °C", "rain", 15, 0.6, 0.5);
    static final WeatherConditions DROUGHT =
            new WeatherConditions(Kind.DROUGHT, "Drought, 29 °C", "drought", 29, 0, 2.5);
    static final WeatherConditions HEATWAVE =
            new WeatherConditions(Kind.HEATWAVE, "Heatwave, 36 °C", "heatwave", 36, 0, 2.0);
    static final WeatherConditions COLD = new WeatherConditions(Kind.COLD, "Cold snap, 3 °C", "cold", 3, 0, 0.4);

    WeatherConditions withSource(String newSource) {
        return new WeatherConditions(kind, label, newSource, temperatureTarget, rainPerStep, evaporationFactor);
    }
}
