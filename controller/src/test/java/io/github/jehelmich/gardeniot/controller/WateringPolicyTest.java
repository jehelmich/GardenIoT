package io.github.jehelmich.gardeniot.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WateringPolicyTest {

    private static final Instant T0 = Instant.parse("2017-07-17T10:15:30Z");
    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = T0;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final WateringPolicy policy = new WateringPolicy(25.0, COOLDOWN, clock);

    private static Telemetry reading(String deviceId, double humidity) {
        return new Telemetry(deviceId, T0, 22.0, humidity);
    }

    @Test
    void watersWhenTheSoilIsBelowTheThreshold() {
        assertThat(policy.shouldWater(reading("garden-1", 24.9))).isTrue();
    }

    @Test
    void doesNotWaterAtOrAboveTheThreshold() {
        assertThat(policy.shouldWater(reading("garden-1", 25.0))).isFalse();
        assertThat(policy.shouldWater(reading("garden-1", 80.0))).isFalse();
    }

    @Test
    void doesNotRepeatWithinTheCooldown() {
        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isTrue();

        clock.advance(COOLDOWN.minusSeconds(1));

        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isFalse();
    }

    @Test
    void watersAgainOnceTheCooldownHasPassed() {
        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isTrue();

        clock.advance(COOLDOWN);

        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isTrue();
    }

    @Test
    void tracksTheCooldownPerDevice() {
        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isTrue();

        assertThat(policy.shouldWater(reading("garden-2", 10.0))).isTrue();
        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isFalse();
    }

    @Test
    void aWetReadingDoesNotResetTheCooldown() {
        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isTrue();
        clock.advance(Duration.ofSeconds(30));
        assertThat(policy.shouldWater(reading("garden-1", 90.0))).isFalse();
        clock.advance(Duration.ofSeconds(30));

        assertThat(policy.shouldWater(reading("garden-1", 10.0))).isTrue();
    }
}
