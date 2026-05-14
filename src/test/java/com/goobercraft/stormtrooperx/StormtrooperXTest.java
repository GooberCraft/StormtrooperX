package com.goobercraft.stormtrooperx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.goobercraft.stormtrooperx.support.TestSupport;

/**
 * Tests for {@link StormtrooperX} utility methods and the inner
 * {@link StormtrooperX.EntityConfig} class.
 */
@DisplayName("StormtrooperX — utility methods + EntityConfig")
class StormtrooperXTest {

    @Nested
    @DisplayName("capitalize(String)")
    class Capitalize {

        @ParameterizedTest(name = "capitalize(\"{0}\") -> \"{1}\"")
        @CsvSource({
            "skeleton, Skeleton",
            "Skeleton, Skeleton",
            "a,        A",
        })
        void capitalizesAsExpected(String input, String expected) {
            assertThat((String) TestSupport.invokePrivate(StormtrooperX.class, "capitalize", input))
                .isEqualTo(expected);
        }

        @Test
        @DisplayName("empty string is returned unchanged")
        void emptyString() {
            assertThat((String) TestSupport.invokePrivate(StormtrooperX.class, "capitalize", ""))
                .isEmpty();
        }

        @Test
        @DisplayName("null is returned as null (no NPE)")
        void nullString() {
            assertThat((String) TestSupport.invokePrivate(StormtrooperX.class, "capitalize", new Object[]{null}))
                .isNull();
        }
    }

    @Nested
    @DisplayName("sanitizeForLog(String) — CWE-117 log-injection guard")
    class SanitizeForLog {

        @Test
        @DisplayName("strips a CR used to forge a log line")
        void stripsCarriageReturn() {
            assertThat(StormtrooperX.sanitizeForLog("1.9.0\rINFO forged"))
                .isEqualTo("1.9.0INFO forged");
        }

        @Test
        @DisplayName("strips an LF used to forge a log line")
        void stripsLineFeed() {
            assertThat(StormtrooperX.sanitizeForLog("1.9.0\nWARNING fake"))
                .isEqualTo("1.9.0WARNING fake");
        }

        @Test
        @DisplayName("strips every CR/LF in a multi-line injection payload")
        void stripsAllLineBreaks() {
            assertThat(StormtrooperX.sanitizeForLog("1.9.0\r\n\r\nx"))
                .isEqualTo("1.9.0x");
        }

        @ParameterizedTest(name = "leaves clean version [{0}] unchanged")
        @ValueSource(strings = {"1.9.0", "v2.0.0-rc1", "1.0.0+build.123", ""})
        void cleanValuesUnchanged(String input) {
            assertThat(StormtrooperX.sanitizeForLog(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("null becomes the literal \"null\" (no NPE at the sink)")
        void nullBecomesLiteral() {
            assertThat(StormtrooperX.sanitizeForLog(null)).isEqualTo("null");
        }
    }

    @Nested
    @DisplayName("EntityConfig — enabled flag")
    class EntityConfigEnabled {

        @Test
        @DisplayName("enabled=true is reported by isEnabled()")
        void enabled() {
            assertThat(new StormtrooperX.EntityConfig(true, 0.7).isEnabled()).isTrue();
        }

        @Test
        @DisplayName("enabled=false is reported by isEnabled()")
        void disabled() {
            assertThat(new StormtrooperX.EntityConfig(false, 0.5).isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("EntityConfig — accuracy clamping")
    class EntityConfigClamping {

        @ParameterizedTest(name = "accuracy={0} is kept verbatim")
        @ValueSource(doubles = {0.0, 0.25, 0.5, 0.7, 1.0})
        void inRangeValuesPreserved(double accuracy) {
            assertThat(new StormtrooperX.EntityConfig(true, accuracy).getAccuracy())
                .isCloseTo(accuracy, within(1e-9));
        }

        @ParameterizedTest(name = "accuracy={0} clamps to 1.0")
        @ValueSource(doubles = {1.5, 2.0, 999.9})
        void aboveMaxClampsToOne(double accuracy) {
            assertThat(new StormtrooperX.EntityConfig(true, accuracy).getAccuracy())
                .isCloseTo(1.0, within(1e-9));
        }

        @ParameterizedTest(name = "accuracy={0} clamps to 0.0")
        @ValueSource(doubles = {-0.5, -1.0, -999.9})
        void belowMinClampsToZero(double accuracy) {
            assertThat(new StormtrooperX.EntityConfig(true, accuracy).getAccuracy())
                .isCloseTo(0.0, within(1e-9));
        }
    }
}
