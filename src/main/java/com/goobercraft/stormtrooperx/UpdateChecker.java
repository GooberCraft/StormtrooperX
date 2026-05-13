package com.goobercraft.stormtrooperx;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.regex.Pattern;

import com.goobercraft.stormtrooperx.scheduler.PluginScheduler;

/**
 * Utility class to check for plugin updates from GitHub Releases.
 */
public class UpdateChecker {

    private final Plugin plugin;
    private final PluginScheduler scheduler;
    private final String githubRepo;
    private String latestVersion;

    // Maximum allowed response size to prevent memory exhaustion attacks
    private static final int MAX_RESPONSE_SIZE_BYTES = 1024 * 1024; // 1MB

    // GitHub's actual owner/repo naming rules: alphanumeric, dot, hyphen, underscore.
    // Defense-in-depth: rejects path-traversal segments, query/fragment delimiters,
    // and JDBC/log-injection control characters even though the host is fixed to
    // api.github.com at the call site.
    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$");

    // Strict semver-ish shape for release tags fetched from GitHub. The remote response
    // is untrusted (could be tampered if the release page is compromised or TLS bypassed),
    // so we reject anything that wouldn't be a valid version string before it reaches
    // log lines or version comparison. Closes CWE-117 (log injection) and hardens
    // parseVersionPart against malformed input.
    //
    // Allows: optional leading 'v', 1-4 numeric segments, optional pre-release/build
    // suffix of [A-Za-z0-9.-] separated by '-' or '+'.
    // Examples accepted: 1.9.0, v1.9.0, 1.9.0-rc1, 1.0.0+build.123, 1.0.0-SNAPSHOT
    // Examples rejected: anything with whitespace, control chars, or non-version syntax
    private static final Pattern RELEASE_TAG_PATTERN = Pattern.compile(
        "^v?\\d+(\\.\\d+){0,3}([-+][A-Za-z0-9.-]+)?$");

    /**
     * Creates a new update checker.
     *
     * @param plugin The plugin instance (used for logging and version lookup)
     * @param scheduler Scheduler abstraction for async dispatch (must not be null)
     * @param githubRepo The GitHub repository in format "owner/repo"
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public UpdateChecker(Plugin plugin, PluginScheduler scheduler, String githubRepo) {
        // Defensive: validate constructor parameters
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler cannot be null");
        }
        if (githubRepo == null || githubRepo.isEmpty()) {
            throw new IllegalArgumentException("githubRepo cannot be null or empty");
        }
        if (!GITHUB_REPO_PATTERN.matcher(githubRepo).matches()) {
            throw new IllegalArgumentException("githubRepo must be in format 'owner/repo', got: " + githubRepo);
        }

        this.plugin = plugin;
        this.scheduler = scheduler;
        this.githubRepo = githubRepo;
    }

    /**
     * Checks for updates asynchronously.
     *
     * @param callback Callback to run after check completes
     */
    public void checkForUpdates(UpdateCallback callback) {
        scheduler.runAsync(() -> {
            try {
                final String currentVersion = plugin.getDescription().getVersion();
                latestVersion = fetchLatestVersion();

                if (latestVersion == null) {
                    plugin.getLogger().warning("Failed to check for updates. Could not fetch latest version.");
                    return;
                }

                final int comparison = compareVersions(currentVersion, latestVersion);
                callback.onComplete(comparison, currentVersion, latestVersion);

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
            final URL url = new URL("https://api.github.com/repos/" + githubRepo + "/releases/latest");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "StormtrooperX-UpdateChecker");

            final int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Limit response size to prevent memory exhaustion attacks
                final int contentLength = connection.getContentLength();
                if (contentLength > MAX_RESPONSE_SIZE_BYTES) {
                    plugin.getLogger().warning("Response size too large, possible security issue");
                    return null;
                }
                // Note: contentLength may be -1 if server doesn't provide Content-Length header
                // The runtime check below will still protect against large responses

                // Use try-with-resources to ensure proper resource cleanup
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    final StringBuilder response = new StringBuilder();
                    String line;
                    int totalChars = 0;
                    while ((line = reader.readLine()) != null) {
                        totalChars += line.length();
                        // Runtime check to prevent memory exhaustion attacks
                        // Note: We use character count rather than byte count for simplicity.
                        // In Java, chars are UTF-16 (2 bytes each), so actual memory usage may be 2x this limit,
                        // but the limit is generous (1MB of chars = ~2MB memory) and sufficient for legitimate
                        // GitHub API responses (~10KB typical). This is a security control, not exact accounting.
                        if (totalChars > MAX_RESPONSE_SIZE_BYTES) {
                            plugin.getLogger().warning("Response too large, aborting");
                            return null;
                        }
                        response.append(line);
                    }

                    // Parse JSON response to get tag_name
                    final String json = response.toString();
                    final String tagName = extractJsonValue(json, "tag_name");

                    final String validated = validateReleaseTag(tagName);
                    if (validated == null && tagName != null) {
                        // Tag was present but did not match the strict shape; refuse to
                        // propagate it so log lines and version compare see only safe input.
                        plugin.getLogger().warning(
                            "Discarding malformed release tag from GitHub API; update check skipped.");
                    }
                    return validated;
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
     * Validates a release tag against the strict {@link #RELEASE_TAG_PATTERN} and
     * strips an optional leading {@code v}. Returns null for any input that does
     * not match (including null) so that callers can treat "no valid version" and
     * "malformed version" uniformly.
     *
     * <p>This is the sole barrier between the untrusted GitHub response and the
     * rest of the plugin (logger, version comparison). Do not bypass it.</p>
     *
     * @param tagName Raw tag string from the GitHub API, or null
     * @return The validated version with any leading {@code v} removed, or null if invalid
     */
    private String validateReleaseTag(String tagName) {
        if (tagName == null || !RELEASE_TAG_PATTERN.matcher(tagName).matches()) {
            return null;
        }
        return tagName.startsWith("v") ? tagName.substring(1) : tagName;
    }

    /**
     * Simple JSON value extractor (avoids needing JSON library dependency).
     *
     * @param json The JSON string
     * @param key The key to extract
     * @return The value, or null if not found
     */
    private String extractJsonValue(String json, String key) {
        final String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();
        final int endIndex = json.indexOf("\"", startIndex);
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
        final String[] currentParts = current.split("\\.");
        final String[] latestParts = latest.split("\\.");

        final int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            final int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            final int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;

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
        final StringBuilder numeric = new StringBuilder();
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
         * <p><b>Threading:</b> invoked on the async worker thread used for the
         * HTTP fetch &mdash; <em>not</em> on a Bukkit region/main thread.
         * Implementations must not touch the Bukkit API. Logging via
         * {@code java.util.logging} is safe.</p>
         *
         * @param comparison Negative if outdated, 0 if up-to-date, positive if ahead
         * @param currentVersion The current plugin version
         * @param latestVersion The latest available version
         */
        void onComplete(int comparison, String currentVersion, String latestVersion);
    }
}
