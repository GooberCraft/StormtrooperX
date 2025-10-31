package com.goobercraft.stormtrooperx;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Scanner;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for plugin resources to ensure Maven resource filtering works correctly.
 */
class PluginResourceTest {

    /**
     * Tests that plugin.yml has the correct version from Maven.
     * This ensures Maven resource filtering is working and the version
     * is not hardcoded or left as a placeholder.
     */
    @Test
    void testPluginVersionIsResolved() {
        InputStream pluginYml = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(pluginYml, "plugin.yml should be available on classpath");

        Scanner scanner = new Scanner(pluginYml);
        String versionLine = null;

        // Find the version line
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.startsWith("version:")) {
                versionLine = line;
                break;
            }
        }
        scanner.close();

        assertNotNull(versionLine, "plugin.yml should contain a 'version:' line");

        // Extract the version value (e.g., "version: '1.2.1'" -> "1.2.1")
        String version = versionLine.substring(versionLine.indexOf(':') + 1).trim();
        version = version.replaceAll("^['\"]|['\"]$", ""); // Remove quotes

        // Verify the version is not the Maven placeholder
        assertNotEquals("${project.version}", version,
                "Version should be resolved by Maven resource filtering, not left as placeholder");

        // Verify the version is not hardcoded to an old value
        assertNotEquals("1.0", version,
                "Version should be dynamically set from pom.xml, not hardcoded to 1.0");

        // Verify the version follows semantic versioning (e.g., 1.2.1, 2.0.0-SNAPSHOT)
        Pattern semverPattern = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?$");
        assertTrue(semverPattern.matcher(version).matches(),
                "Version '" + version + "' should follow semantic versioning format (e.g., 1.2.1 or 1.2.1-SNAPSHOT)");

        System.out.println("✓ plugin.yml version correctly resolved to: " + version);
    }

    /**
     * Tests that the version in plugin.yml is a valid, non-empty string.
     */
    @Test
    void testPluginVersionIsNotEmpty() {
        InputStream pluginYml = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(pluginYml, "plugin.yml should be available on classpath");

        Scanner scanner = new Scanner(pluginYml);
        String versionLine = null;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.startsWith("version:")) {
                versionLine = line;
                break;
            }
        }
        scanner.close();

        assertNotNull(versionLine, "plugin.yml should contain a 'version:' line");

        String version = versionLine.substring(versionLine.indexOf(':') + 1).trim();
        version = version.replaceAll("^['\"]|['\"]$", "");

        assertFalse(version.isEmpty(), "Version should not be empty");
        assertTrue(version.length() > 0, "Version should have content");
    }
}
