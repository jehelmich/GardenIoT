package io.github.jehelmich.gardeniot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

    @Test
    void requiredReturnsTheStrippedValue() {
        assertThat(new Environment(Map.of("KEY", "  value ")).required("KEY")).isEqualTo("value");
    }

    @Test
    void requiredRejectsUnsetAndBlankVariables() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new Environment(Map.of()).required("KEY"))
                .withMessage("Missing required environment variable KEY");
        assertThatIllegalStateException().isThrownBy(() -> new Environment(Map.of("KEY", "   ")).required("KEY"));
    }

    @Test
    void optionalFallsBackToTheDefault() {
        Environment env = new Environment(Map.of("SET", "x", "BLANK", ""));
        assertThat(env.optional("SET", "d")).isEqualTo("x");
        assertThat(env.optional("BLANK", "d")).isEqualTo("d");
        assertThat(env.optional("UNSET", "d")).isEqualTo("d");
    }

    @Test
    void optionalDoubleParsesNumbers() {
        Environment env = new Environment(Map.of("N", "12.5", "BAD", "lots"));
        assertThat(env.optionalDouble("N", 1.0)).isEqualTo(12.5);
        assertThat(env.optionalDouble("UNSET", 1.0)).isEqualTo(1.0);
        assertThatIllegalStateException()
                .isThrownBy(() -> env.optionalDouble("BAD", 1.0))
                .withMessageContaining("BAD must be a number");
    }

    @Test
    void optionalSecondsParsesPositiveWholeSeconds() {
        Environment env = new Environment(Map.of("S", "30", "ZERO", "0", "FRACTION", "1.5"));
        assertThat(env.optionalSeconds("S", Duration.ofSeconds(1))).isEqualTo(Duration.ofSeconds(30));
        assertThat(env.optionalSeconds("UNSET", Duration.ofSeconds(1))).isEqualTo(Duration.ofSeconds(1));
        assertThatIllegalStateException().isThrownBy(() -> env.optionalSeconds("ZERO", Duration.ofSeconds(1)));
        assertThatIllegalStateException().isThrownBy(() -> env.optionalSeconds("FRACTION", Duration.ofSeconds(1)));
    }
}
