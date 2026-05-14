package com.goobercraft.stormtrooperx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.goobercraft.stormtrooperx.scheduler.PluginScheduler;
import com.goobercraft.stormtrooperx.support.InlinePluginScheduler;
import com.goobercraft.stormtrooperx.support.TestSupport;

/**
 * Unit tests for {@link UpdateChecker}. Covers the pure-logic surface
 * (version comparison, JSON parsing, release-tag validation) without
 * hitting GitHub.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateChecker — version comparison, JSON parsing, release-tag validation")
class UpdateCheckerTest {

    @Mock
    private Plugin plugin;

    private PluginScheduler scheduler;
    private UpdateChecker updateChecker;

    @BeforeEach
    void setUp() {
        // plugin.getLogger() / getDescription() are only touched inside checkForUpdates(),
        // not the constructor, so most tests never trigger these stubs — keep them lenient.
        final Logger logger = Logger.getLogger("UpdateCheckerTest");
        final PluginDescriptionFile description = new PluginDescriptionFile("TestPlugin", "1.0.0", "com.test.Main");
        lenient().when(plugin.getLogger()).thenReturn(logger);
        lenient().when(plugin.getDescription()).thenReturn(description);
        scheduler = new InlinePluginScheduler();
        updateChecker = new UpdateChecker(plugin, scheduler, "owner/repo");
    }

    // ---------------------------------------------------------------------
    // Constructor + repo-string validation
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("accepts valid owner/repo string")
        void acceptsValidRepo() {
            assertThat(new UpdateChecker(plugin, scheduler, "owner/repo")).isNotNull();
        }

        @Test
        @DisplayName("accepts GitHub-legal owner/repo names (dots, dashes, underscores)")
        void acceptsValidRepoChars() {
            assertThat(new UpdateChecker(plugin, scheduler, "MyOrg-1/my.repo_v2")).isNotNull();
        }

        @Test
        @DisplayName("rejects null plugin")
        void rejectsNullPlugin() {
            assertThatThrownBy(() -> new UpdateChecker(null, scheduler, "owner/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("plugin cannot be null");
        }

        @Test
        @DisplayName("rejects null scheduler")
        void rejectsNullScheduler() {
            assertThatThrownBy(() -> new UpdateChecker(plugin, null, "owner/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scheduler cannot be null");
        }

        @ParameterizedTest(name = "rejects githubRepo = [{0}]")
        @NullAndEmptySource
        void rejectsNullOrEmptyRepo(String repo) {
            assertThatThrownBy(() -> new UpdateChecker(plugin, scheduler, repo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("githubRepo cannot be null or empty");
        }

        @ParameterizedTest(name = "rejects malformed githubRepo = [{0}]")
        @ValueSource(strings = {
            "invalidrepo",
            "owner/extra/repo",
            "../sensitive/path",
            "owner/repo;DROP TABLE",
            "owner/repo\r\nHost: evil.com",
        })
        void rejectsMalformedRepo(String repo) {
            assertThatThrownBy(() -> new UpdateChecker(plugin, scheduler, repo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("githubRepo must be in format 'owner/repo'");
        }
    }

    // ---------------------------------------------------------------------
    // compareVersions
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("compareVersions(current, latest)")
    class CompareVersions {

        @ParameterizedTest(name = "compareVersions(\"{0}\", \"{1}\") has sign {2}")
        @CsvSource({
            // Basic relations
            "1.0.0, 1.1.0, -1",
            "1.0.0, 1.0.0,  0",
            "2.0.0, 1.0.0,  1",
            "1.0.0, 2.0.0, -1",
            "1.1.0, 1.2.0, -1",
            "1.0.1, 1.0.2, -1",
            // Mixed lengths (missing parts treated as 0)
            "1.0,     1.0.0, 0",
            "1.0.1,   1.0,   1",
            "1,       2,    -1",
            // Multi-digit ordering — string-naive comparison would mis-order these
            "1.2.3,   1.2.10, -1",
            // Non-numeric suffix is stripped
            "1.0-SNAPSHOT, 1.0, 0",
            // Long version strings
            "1.2.3.4.5.6.7.8, 1.2.3.4.5.6.7.9, -1",
            // Four-part versions
            "1.0.0.0, 1.0.0.1, -1",
        })
        void compareVersionsRespectsSign(String current, String latest, int expectedSign) {
            final int result = TestSupport.invokePrivate(updateChecker, "compareVersions", current, latest);
            assertThat(Integer.signum(result)).isEqualTo(expectedSign);
        }

        @ParameterizedTest(name = "compareVersions(\"{0}\", \"{1}\") sign={2}")
        @CsvSource({
            "'',    1.0.0, -1",
            "'',    '',     0",
        })
        void compareVersionsHandlesEmptyStrings(String current, String latest, int expectedSign) {
            final int result = TestSupport.invokePrivate(updateChecker, "compareVersions", current, latest);
            assertThat(Integer.signum(result)).isEqualTo(expectedSign);
        }
    }

    // ---------------------------------------------------------------------
    // parseVersionPart
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("parseVersionPart(part)")
    class ParseVersionPart {

        @ParameterizedTest(name = "parseVersionPart(\"{0}\") = {1}")
        @CsvSource({
            "'123',        123",
            "'1-SNAPSHOT', 1",
            "'2alpha',     2",
            "'',           0",
            "'beta',       0",
        })
        void parsesAllExpectedShapes(String input, int expected) {
            final int result = TestSupport.invokePrivate(updateChecker, "parseVersionPart", input);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns 0 for numbers that overflow Integer")
        void overflowFallsBackToZero() {
            // 21 nines comfortably exceeds Integer.MAX_VALUE (~2.1e9)
            final int result = TestSupport.invokePrivate(updateChecker, "parseVersionPart", "999999999999999999999");
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("returns 0 for pathologically long numeric strings without hanging")
        void veryLargeInputDoesNotHang() {
            final int result = TestSupport.invokePrivate(updateChecker, "parseVersionPart", "9".repeat(10_000));
            assertThat(result).isZero();
        }
    }

    // ---------------------------------------------------------------------
    // extractJsonValue
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("extractJsonValue(json, key)")
    class ExtractJsonValue {

        @ParameterizedTest(name = "{2}")
        @CsvSource(delimiter = '|', value = {
            // json | key | description (used as name)
            "{\"tag_name\":\"v1.2.0\",\"name\":\"Release 1.2.0\"} | tag_name | extracts first key",
            "{\"name\":\"Test\",\"tag_name\":\"v1.0.0\",\"description\":\"Test\"} | tag_name | extracts middle key",
            "{\"tag_name\":\"v1.0.0-beta+build.123\"} | tag_name | preserves special chars",
            "{\"tag_name\":\"v1.0.0\",\"other\":\"data\",\"tag_name\":\"v2.0.0\"} | tag_name | returns first of duplicates",
        })
        void extractsExpectedValues(String json, String key, String description) {
            final String result = TestSupport.invokePrivate(updateChecker, "extractJsonValue", json, key);
            assertThat(result).as(description).isNotNull();
        }

        @Test
        @DisplayName("extracts simple value verbatim")
        void simpleValue() {
            final String result = TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{\"tag_name\":\"v1.2.0\",\"name\":\"Release 1.2.0\"}", "tag_name");
            assertThat(result).isEqualTo("v1.2.0");
        }

        @Test
        @DisplayName("returns first occurrence when key appears twice")
        void firstOccurrence() {
            final String result = TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{\"tag_name\":\"v1.0.0\",\"other\":\"data\",\"tag_name\":\"v2.0.0\"}", "tag_name");
            assertThat(result).isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("extracts empty string value")
        void emptyValue() {
            final String result = TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{\"tag_name\":\"\"}", "tag_name");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns null when key is missing")
        void keyMissing() {
            assertThat((String) TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{\"name\":\"Test\"}", "tag_name")).isNull();
        }

        @Test
        @DisplayName("returns null for empty JSON object")
        void emptyJsonObject() {
            assertThat((String) TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{}", "tag_name")).isNull();
        }

        @Test
        @DisplayName("returns null for malformed JSON (unterminated string)")
        void malformedJson() {
            assertThat((String) TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{\"tag_name\":\"v1.0.0", "tag_name")).isNull();
        }

        @Test
        @DisplayName("preserves escape sequences as raw text (no interpretation)")
        void escapeSequencesNotInterpreted() {
            // The parser must not unescape — that lets the downstream validator/logger
            // decide what to do with suspicious payloads.
            final String result = TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{\"tag_name\":\"v1.0.0\\n[SEVERE] Fake error\"}", "tag_name");
            assertThat(result)
                .isNotNull()
                .contains("\\n");
        }

        @Test
        @DisplayName("known limitation: whitespace around colons is not handled")
        void whitespaceAroundColonNotHandled() {
            // GitHub returns compact JSON without inter-token spaces, so this is acceptable.
            assertThat((String) TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{ \"tag_name\" : \"v1.0.0\" }", "tag_name")).isNull();

            // Whitespace after commas is fine
            assertThat((String) TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                "{\"tag_name\":\"v1.0.0\", \"name\":\"Release\"}", "tag_name"))
                .isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("scales to large responses")
        void largeResponse() {
            final String largeJson = "{\"other_field\":\"" + "x".repeat(1000) + "\",\"tag_name\":\"v1.0.0\"}";
            final String result = TestSupport.invokePrivate(updateChecker, "extractJsonValue",
                largeJson, "tag_name");
            assertThat(result).isEqualTo("v1.0.0");
        }
    }

    // ---------------------------------------------------------------------
    // validateReleaseTag — security barrier between GitHub response and logger
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("validateReleaseTag — log-injection barrier (CWE-117)")
    class ValidateReleaseTag {

        @ParameterizedTest(name = "accepts [{0}] -> [{1}]")
        @CsvSource({
            "1.9.0,           1.9.0",
            "v1.9.0,          1.9.0",
            "1.9.0-rc1,       1.9.0-rc1",
            "v1.0.0-SNAPSHOT, 1.0.0-SNAPSHOT",
            "1.0.0+build.123, 1.0.0+build.123",
            "1,               1",
            "1.2,             1.2",
            "1.2.3.4,         1.2.3.4",
        })
        void acceptsValidTags(String input, String expected) {
            final String result = TestSupport.invokePrivate(updateChecker, "validateReleaseTag", input);
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest(name = "rejects [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {
            // Newline / control-character injection (the core attack)
            "1.9.0\nFAKE LOG ENTRY",
            "1.9.0\r\nFAKE",
            "1.9.0\t",
            "1.9.0 ",
            // Surrounding whitespace
            " 1.9.0",
            "1.9 .0",
            // HTML / shell metacharacters
            "1.9.0<script>",
            "1.9.0; rm -rf /",
            "1.9.0|cat",
            "1.9.0$(whoami)",
            // Too many segments
            "1.2.3.4.5",
            // Non-numeric first segment
            "release-1.0",
            "vv1.0.0",
        })
        void rejectsInvalidTags(String tag) {
            assertThat((String) TestSupport.invokePrivate(updateChecker, "validateReleaseTag", tag)).isNull();
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("public API")
    class PublicApi {

        @Test
        @DisplayName("getLatestVersion() is null until checkForUpdates runs")
        void initiallyNull() {
            assertThat(updateChecker.getLatestVersion()).isNull();
        }
    }
}
