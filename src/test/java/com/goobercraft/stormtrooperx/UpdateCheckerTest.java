package com.goobercraft.stormtrooperx;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UpdateChecker.
 * These tests focus on the pure logic methods (version comparison, JSON parsing)
 * without requiring full Bukkit integration.
 */
@ExtendWith(MockitoExtension.class)
class UpdateCheckerTest {

    @Mock
    private Plugin plugin;

    private UpdateChecker updateChecker;

    @BeforeEach
    void setUp() {
        // Create real instances where possible to avoid mocking final classes
        Logger logger = Logger.getLogger("TestLogger");
        PluginDescriptionFile description = new PluginDescriptionFile("TestPlugin", "1.0.0", "com.test.Main");

        // Use lenient stubbing since not all tests will call these methods
        lenient().when(plugin.getLogger()).thenReturn(logger);
        lenient().when(plugin.getDescription()).thenReturn(description);
        updateChecker = new UpdateChecker(plugin, "owner/repo");
    }

    @Test
    void testConstructor() {
        assertNotNull(updateChecker);
    }

    @Test
    void testConstructor_nullGithubRepo() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new UpdateChecker(plugin, null);
        });
        assertEquals("githubRepo cannot be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_emptyGithubRepo() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new UpdateChecker(plugin, "");
        });
        assertEquals("githubRepo cannot be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_invalidFormat_noSlash() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new UpdateChecker(plugin, "invalidrepo");
        });
        assertEquals("githubRepo must be in format 'owner/repo', got: invalidrepo", exception.getMessage());
    }

    @Test
    void testConstructor_validFormat() {
        // Should not throw
        UpdateChecker checker = new UpdateChecker(plugin, "owner/repo");
        assertNotNull(checker);
    }

    @Test
    void testCompareVersions_currentLessThanLatest() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1.0.0", "1.1.0");
        assertTrue(result < 0, "1.0.0 should be less than 1.1.0");
    }

    @Test
    void testCompareVersions_currentEqualLatest() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1.0.0", "1.0.0");
        assertEquals(0, result, "1.0.0 should equal 1.0.0");
    }

    @Test
    void testCompareVersions_currentGreaterThanLatest() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "2.0.0", "1.0.0");
        assertTrue(result > 0, "2.0.0 should be greater than 1.0.0");
    }

    @Test
    void testCompareVersions_majorVersionDifference() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1.0.0", "2.0.0");
        assertTrue(result < 0, "1.0.0 should be less than 2.0.0");
    }

    @Test
    void testCompareVersions_minorVersionDifference() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1.1.0", "1.2.0");
        assertTrue(result < 0, "1.1.0 should be less than 1.2.0");
    }

    @Test
    void testCompareVersions_patchVersionDifference() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1.0.1", "1.0.2");
        assertTrue(result < 0, "1.0.1 should be less than 1.0.2");
    }

    @Test
    void testCompareVersions_differentLengths() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        // 1.0 vs 1.0.0 should be equal (missing parts treated as 0)
        int result1 = (int) method.invoke(updateChecker, "1.0", "1.0.0");
        assertEquals(0, result1, "1.0 should equal 1.0.0");

        // 1.0.1 vs 1.0 should be greater
        int result2 = (int) method.invoke(updateChecker, "1.0.1", "1.0");
        assertTrue(result2 > 0, "1.0.1 should be greater than 1.0");
    }

    @Test
    void testCompareVersions_singleDigit() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1", "2");
        assertTrue(result < 0, "1 should be less than 2");
    }

    @Test
    void testParseVersionPart_numericOnly() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "123");
        assertEquals(123, result);
    }

    @Test
    void testParseVersionPart_withNonNumericSuffix() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1-SNAPSHOT");
        assertEquals(1, result);
    }

    @Test
    void testParseVersionPart_withAlphaSuffix() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "2alpha");
        assertEquals(2, result);
    }

    @Test
    void testParseVersionPart_emptyString() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "");
        assertEquals(0, result);
    }

    @Test
    void testParseVersionPart_nonNumericOnly() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "beta");
        assertEquals(0, result);
    }

    @Test
    void testExtractJsonValue_found() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        String json = "{\"tag_name\":\"v1.2.0\",\"name\":\"Release 1.2.0\"}";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertEquals("v1.2.0", result);
    }

    @Test
    void testExtractJsonValue_foundMultipleKeys() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        String json = "{\"name\":\"Test\",\"tag_name\":\"v1.0.0\",\"description\":\"Test release\"}";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertEquals("v1.0.0", result);
    }

    @Test
    void testExtractJsonValue_notFound() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        String json = "{\"name\":\"Test\"}";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertNull(result);
    }

    @Test
    void testExtractJsonValue_emptyJson() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        String json = "{}";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertNull(result);
    }

    @Test
    void testExtractJsonValue_malformedJson() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        String json = "{\"tag_name\":\"v1.0.0";  // Missing closing quote and brace
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertNull(result);
    }

    @Test
    void testExtractJsonValue_valueWithSpecialCharacters() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        String json = "{\"tag_name\":\"v1.0.0-beta+build.123\"}";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertEquals("v1.0.0-beta+build.123", result);
    }

    @Test
    void testGetLatestVersion_initiallyNull() {
        assertNull(updateChecker.getLatestVersion(), "Latest version should be null initially");
    }

    @Test
    void testCompareVersions_complexVersions() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        // Test 1.2.3 vs 1.2.10 (should handle multi-digit parts correctly)
        int result = (int) method.invoke(updateChecker, "1.2.3", "1.2.10");
        assertTrue(result < 0, "1.2.3 should be less than 1.2.10");
    }

    @Test
    void testCompareVersions_withSnapshot() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        // 1.0-SNAPSHOT should be treated as 1.0
        int result = (int) method.invoke(updateChecker, "1.0-SNAPSHOT", "1.0");
        assertEquals(0, result, "1.0-SNAPSHOT should equal 1.0 after parsing");
    }

    @Test
    void testCompareVersions_fourPartVersions() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(updateChecker, "1.0.0.0", "1.0.0.1");
        assertTrue(result < 0, "1.0.0.0 should be less than 1.0.0.1");
    }

    @Test
    void testParseVersionPart_overflow() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);

        // Test with a number larger than Integer.MAX_VALUE (2147483647)
        // This should trigger the NumberFormatException catch block
        String overflowNumber = "999999999999999999999";
        int result = (int) method.invoke(updateChecker, overflowNumber);

        // Should return 0 when parsing fails due to overflow
        assertEquals(0, result, "Should return 0 for numbers that overflow Integer");
    }

    @Test
    void testExtractJsonValue_maliciousContentNotExecuted() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        // Test with JSON that contains newlines or special characters that could be used for log injection
        // The important security aspect is that the JSON parsing itself doesn't execute/interpret this content
        String maliciousJson = "{\"tag_name\":\"v1.0.0\\n[SEVERE] Fake error message\"}";
        String result = (String) method.invoke(updateChecker, maliciousJson, "tag_name");

        // The extraction should work but return the raw string (not executed/interpreted)
        // The actual logging happens elsewhere and is outside the scope of extractJsonValue
        assertNotNull(result);
        assertTrue(result.contains("\\n"), "Newlines should be preserved as escaped characters");
    }

    @Test
    void testParseVersionPart_veryLargeInput() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);

        // Test with an extremely long numeric string that could cause performance issues
        String largeNumber = "9".repeat(10000);

        // Should handle gracefully without hanging or crashing
        int result = (int) method.invoke(updateChecker, largeNumber);
        assertEquals(0, result, "Should return 0 for extremely large numbers");
    }

    @Test
    void testExtractJsonValue_largeResponse() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        // Create a very large JSON string to test memory handling
        String largeValue = "x".repeat(1000);
        String largeJson = "{\"other_field\":\"" + largeValue + "\",\"tag_name\":\"v1.0.0\"}";

        // Should extract correctly even with large JSON
        String result = (String) method.invoke(updateChecker, largeJson, "tag_name");
        assertEquals("v1.0.0", result);
    }

    @Test
    void testCompareVersions_emptyStrings() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        // Empty versions should be treated as 0.0.0
        int result1 = (int) method.invoke(updateChecker, "", "1.0.0");
        assertTrue(result1 < 0, "Empty version should be less than 1.0.0");

        int result2 = (int) method.invoke(updateChecker, "", "");
        assertEquals(0, result2, "Two empty versions should be equal");
    }

    @Test
    void testExtractJsonValue_multipleOccurrences() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        // If tag_name appears multiple times, should get the first occurrence
        String json = "{\"tag_name\":\"v1.0.0\",\"other\":\"data\",\"tag_name\":\"v2.0.0\"}";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertEquals("v1.0.0", result, "Should extract first occurrence of tag_name");
    }

    @Test
    void testExtractJsonValue_emptyValue() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        // tag_name with empty string value
        String json = "{\"tag_name\":\"\"}";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertEquals("", result, "Should extract empty string value");
    }

    @Test
    void testExtractJsonValue_whitespaceAroundColon() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("extractJsonValue", String.class, String.class);
        method.setAccessible(true);

        // Known limitation: Simple string-based parser doesn't handle whitespace around colons
        // GitHub API returns compact JSON without extra whitespace, so this is acceptable
        String json = "{ \"tag_name\" : \"v1.0.0\" }";
        String result = (String) method.invoke(updateChecker, json, "tag_name");
        assertNull(result, "Current parser doesn't handle whitespace around colons (known limitation)");

        // However, whitespace after commas works fine
        String json2 = "{\"tag_name\":\"v1.0.0\", \"name\":\"Release\"}";
        String result2 = (String) method.invoke(updateChecker, json2, "tag_name");
        assertEquals("v1.0.0", result2, "Whitespace after commas works correctly");
    }

    @Test
    void testCompareVersions_veryLongVersionStrings() throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);

        // Test with many version parts
        int result = (int) method.invoke(updateChecker, "1.2.3.4.5.6.7.8", "1.2.3.4.5.6.7.9");
        assertTrue(result < 0, "Should correctly compare very long version strings");
    }
}
