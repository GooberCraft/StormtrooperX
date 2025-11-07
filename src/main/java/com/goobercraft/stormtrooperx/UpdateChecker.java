package com.goobercraft.stormtrooperx;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;

/**
 * Utility class to check for plugin updates from GitHub Releases.
 */
public class UpdateChecker {

    private final Plugin plugin;
    private final String githubRepo;
    private String latestVersion;

    /**
     * Creates a new update checker.
     *
     * @param plugin The plugin instance
     * @param githubRepo The GitHub repository in format "owner/repo"
     */
    public UpdateChecker(Plugin plugin, String githubRepo) {
        this.plugin = plugin;
        this.githubRepo = githubRepo;
    }

    /**
     * Checks for updates asynchronously.
     *
     * @param callback Callback to run after check completes
     */
    public void checkForUpdates(UpdateCallback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                latestVersion = fetchLatestVersion();

                if (latestVersion == null) {
                    plugin.getLogger().warning("Failed to check for updates. Could not fetch latest version.");
                    return;
                }

                int comparison = compareVersions(currentVersion, latestVersion);

                // Run callback on main thread
                Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(comparison, currentVersion, latestVersion));

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates", e);
            }
        });
    }

    /**
     * Fetches the latest version from GitHub Releases API.
     *
     * @return The latest version string, or null if failed
     */
    private String fetchLatestVersion() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.github.com/repos/" + githubRepo + "/releases/latest");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "StormtrooperX-UpdateChecker");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Limit response size to prevent memory exhaustion attacks
                int contentLength = connection.getContentLength();
                if (contentLength > 1024 * 1024) { // 1MB limit
                    plugin.getLogger().warning("Response size too large, possible security issue");
                    return null;
                }

                // Use try-with-resources to ensure proper resource cleanup
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    int totalChars = 0;
                    while ((line = reader.readLine()) != null) {
                        totalChars += line.length();
                        // Additional runtime check to prevent memory exhaustion
                        if (totalChars > 1024 * 1024) { // 1MB limit
                            plugin.getLogger().warning("Response too large, aborting");
                            return null;
                        }
                        response.append(line);
                    }

                    // Parse JSON response to get tag_name
                    String json = response.toString();
                    String tagName = extractJsonValue(json, "tag_name");

                    // Remove 'v' prefix if present
                    if (tagName != null && tagName.startsWith("v")) {
                        return tagName.substring(1);
                    }
                    return tagName;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error fetching latest version from GitHub", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    /**
     * Simple JSON value extractor (avoids needing JSON library dependency).
     *
     * @param json The JSON string
     * @param key The key to extract
     * @return The value, or null if not found
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return null;
        }
        return json.substring(startIndex, endIndex);
    }

    /**
     * Compares two version strings.
     *
     * @param current Current version
     * @param latest Latest version
     * @return Negative if current < latest, 0 if equal, positive if current > latest
     */
    private int compareVersions(String current, String latest) {
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");

        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;

            if (currentPart < latestPart) {
                return -1;
            } else if (currentPart > latestPart) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Parses a version part to an integer, handling non-numeric suffixes.
     *
     * @param part Version part string
     * @return Numeric value
     */
    private int parseVersionPart(String part) {
        // Remove any non-numeric suffix (e.g., "1.0-SNAPSHOT" -> "1.0")
        StringBuilder numeric = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                break;
            }
        }

        if (numeric.length() == 0) {
            return 0;
        }

        try {
            return Integer.parseInt(numeric.toString());
        } catch (NumberFormatException e) {
            // Handle overflow or invalid number
            // Only log safe, sanitized information to prevent log injection
            plugin.getLogger().warning("Failed to parse version number: invalid numeric format");
            return 0;
        }
    }

    /**
     * Gets the latest version that was fetched.
     *
     * @return Latest version string
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Callback interface for update checks.
     */
    public interface UpdateCallback {
        /**
         * Called when the update check completes.
         *
         * @param comparison Negative if outdated, 0 if up-to-date, positive if ahead
         * @param currentVersion The current plugin version
         * @param latestVersion The latest available version
         */
        void onComplete(int comparison, String currentVersion, String latestVersion);
    }
}
