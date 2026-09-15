package io.github.jehelmich.gardeniot.device;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Where a pot's weather comes from. */
public interface WeatherProvider {

    WeatherConditions current();

    /** Fixed weather, for a predictable demo or test. */
    static WeatherProvider fixed(WeatherConditions conditions) {
        return () -> conditions;
    }

    /**
     * Weather that changes on its own every couple of minutes. Derived from the wall clock, not
     * from randomness, so every device process in a garden sees the same weather without talking
     * to each other.
     */
    static WeatherProvider auto(Clock clock) {
        return new Auto(clock);
    }

    /** Parses a fixed mode: clear, rain, drought, heatwave, cold. */
    static Optional<WeatherConditions> fixedMode(String mode) {
        if (mode == null) {
            return Optional.empty();
        }
        return switch (mode.strip().toLowerCase(Locale.ROOT)) {
            case "clear" -> Optional.of(WeatherConditions.CLEAR);
            case "rain" -> Optional.of(WeatherConditions.RAIN);
            case "drought" -> Optional.of(WeatherConditions.DROUGHT);
            case "heatwave", "heat" -> Optional.of(WeatherConditions.HEATWAVE);
            case "cold" -> Optional.of(WeatherConditions.COLD);
            default -> Optional.empty();
        };
    }

    final class Auto implements WeatherProvider {
        static final long PERIOD_SECONDS = 120;
        private static final List<WeatherConditions> SEQUENCE = List.of(
                WeatherConditions.CLEAR,
                WeatherConditions.CLEAR,
                WeatherConditions.RAIN,
                WeatherConditions.CLEAR,
                WeatherConditions.HEATWAVE,
                WeatherConditions.CLEAR,
                WeatherConditions.DROUGHT,
                WeatherConditions.RAIN,
                WeatherConditions.COLD,
                WeatherConditions.CLEAR,
                WeatherConditions.RAIN,
                WeatherConditions.HEATWAVE);
        private final Clock clock;

        Auto(Clock clock) {
            this.clock = clock;
        }

        @Override
        public WeatherConditions current() {
            return at(clock.instant());
        }

        static WeatherConditions at(Instant now) {
            long bucket = Math.floorDiv(now.getEpochSecond(), PERIOD_SECONDS);
            // A cheap hash so neighbouring buckets do not just walk the list in order.
            long mixed = (bucket * 2654435761L) ^ (bucket >>> 3);
            return SEQUENCE.get((int) Math.floorMod(mixed, SEQUENCE.size())).withSource("auto");
        }
    }
}
