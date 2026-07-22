package com.goobercraft.stormtrooperx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database operations for player opt-out preferences.
 * Supports both H2 (embedded) and MySQL (with HikariCP connection pooling).
 */
public class DatabaseManager {

    private final Logger logger;
    private final String databaseType;
    // Cached at construction so hot DB paths skip repeated string compares.
    private final boolean isH2;
    private final File dataFolder;
    private final ConfigurationSection mysqlConfig;

    // Single long-lived H2 connection (no pool).
    private Connection h2Connection;

    // Serializes access to the shared H2 connection — JDBC Connections are not
    // thread-safe and async tasks can otherwise race on it. The MySQL path uses
    // HikariCP (a connection per thread) and needs no such lock.
    private final Object h2Lock = new Object();

    private HikariDataSource hikariDataSource;

    /**
     * Allowlist of MySQL Connector/J properties admins may set under
     * {@code database.mysql.properties}. Restricted to TLS, time/encoding,
     * network, and safe behavior flags; properties with RCE / file-read history
     * ({@code allowLoadLocalInfile}, {@code autoDeserialize},
     * {@code queryInterceptors}, ...) are deliberately absent. Extend this list
     * rather than removing the allowlist.
     */
    static final Set<String> SAFE_MYSQL_PROPERTY_KEYS = Set.of(
        // SSL/TLS
        "useSSL", "requireSSL", "verifyServerCertificate", "sslMode",
        "trustCertificateKeyStoreUrl", "trustCertificateKeyStoreType",
        "trustCertificateKeyStorePassword",
        "clientCertificateKeyStoreUrl", "clientCertificateKeyStoreType",
        "clientCertificateKeyStorePassword",
        "enabledSSLCipherSuites", "enabledTLSProtocols",
        // Time and character handling
        "serverTimezone", "connectionTimeZone", "characterEncoding",
        "connectionCollation", "useUnicode",
        // Network behavior
        "connectTimeout", "socketTimeout", "tcpKeepAlive", "tcpNoDelay",
        "autoReconnect", "autoReconnectForPools", "maxReconnects", "initialTimeout",
        // Misc safe behavior
        "zeroDateTimeBehavior",
        // Statement caching (callers may override the in-code defaults if needed)
        "cachePrepStmts", "prepStmtCacheSize", "prepStmtCacheSqlLimit", "useServerPrepStmts"
    );
    // 'sessionVariables' is intentionally excluded: its value needs '=', which
    // collides with the strict value check below, and it allows arbitrary SET
    // at connect time. Add it with a dedicated validator if ever needed.

    /**
     * Creates a new database manager.
     *
     * @param logger Logger instance (must not be null)
     * @param dataFolder Plugin data folder (must not be null)
     * @param databaseType Database type: "h2" or "mysql" (must not be null)
     * @param mysqlConfig MySQL configuration section (required if databaseType is "mysql")
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public DatabaseManager(Logger logger, File dataFolder, String databaseType, ConfigurationSection mysqlConfig) {
        if (logger == null) {
            throw new IllegalArgumentException("logger cannot be null");
        }
        if (dataFolder == null) {
            throw new IllegalArgumentException("dataFolder cannot be null");
        }
        if (databaseType == null || databaseType.isEmpty()) {
            throw new IllegalArgumentException("databaseType cannot be null or empty");
        }
        if (!databaseType.equalsIgnoreCase("h2") && !databaseType.equalsIgnoreCase("mysql")) {
            throw new IllegalArgumentException("databaseType must be 'h2' or 'mysql', got: " + databaseType);
        }
        if (databaseType.equalsIgnoreCase("mysql") && mysqlConfig == null) {
            throw new IllegalArgumentException("mysqlConfig is required when databaseType is 'mysql'");
        }

        this.logger = logger;
        this.dataFolder = dataFolder;
        // Locale.ROOT: deterministic case fold across JVM locales (e.g. Turkish I/İ).
        this.databaseType = databaseType.toLowerCase(Locale.ROOT);
        this.isH2 = this.databaseType.equals("h2");
        this.mysqlConfig = mysqlConfig;
    }

    /**
     * Initializes the database connection and creates tables.
     */
    public void initialize() {
        try {
            if (isH2) {
                initializeH2();
            } else {
                initializeMySQL();
            }

            createTables();

            logger.info("Database initialized successfully (" + databaseType.toUpperCase() + ")");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Failed to load database driver", e);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    /**
     * Initializes H2 in <b>embedded</b> mode — file-backed, in-process, no TCP listener.
     *
     * <p>Do not add {@code AUTO_SERVER=TRUE} or switch to {@code jdbc:h2:tcp://...}
     * without revisiting the hardcoded {@code sa}/empty password — it is only
     * safe because there is no network exposure here.</p>
     */
    private void initializeH2() throws ClassNotFoundException, SQLException {
        Class.forName("org.h2.Driver");

        // FILE_LOCK=SOCKET guards the file against concurrent access; MODE=MySQL
        // gives MySQL grammar compatibility.
        final File databaseFile = new File(dataFolder, "players");
        final String url = "jdbc:h2:" + databaseFile.getAbsolutePath() + ";MODE=MySQL;FILE_LOCK=SOCKET";
        h2Connection = DriverManager.getConnection(url, "sa", "");
    }

    /**
     * Validates MySQL connection parameters to prevent JDBC URL injection attacks.
     *
     * @param host MySQL server hostname
     * @param port MySQL server port
     * @param database MySQL database name
     * @param username MySQL username
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private void validateMySQLParameters(String host, int port, String database, String username) {
        if (host == null || !host.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid MySQL host format: " + host +
                ". Host must contain only alphanumeric characters, dots, hyphens, and underscores.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid MySQL port: " + port +
                ". Port must be between 1 and 65535.");
        }
        if (database == null || !database.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid MySQL database name: " + database +
                ". Database name must contain only alphanumeric characters and underscores.");
        }
        if (username == null || !username.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid MySQL username: " + username +
                ". Username must contain only alphanumeric characters, underscores, and hyphens.");
        }
    }

    /**
     * Validates a user-supplied JDBC property key/value pair before it is
     * appended to the JDBC URL. Keys must be in {@link #SAFE_MYSQL_PROPERTY_KEYS};
     * values must not contain {@code &}, {@code =}, or control characters (which
     * could smuggle extra URL parameters). Valid values are URL-encoded at the
     * call site.
     *
     * @throws IllegalArgumentException if the key is not allowlisted, the value
     *         is null, or the value contains a forbidden character
     */
    void validateMySQLProperty(String key, String value) {
        if (key == null || !SAFE_MYSQL_PROPERTY_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unsupported MySQL JDBC property: '" + key
                + "'. Allowed keys: " + SAFE_MYSQL_PROPERTY_KEYS
                + ". If you need a property not on this list, please open an issue at "
                + "https://github.com/GooberCraft/StormtrooperX/issues so the allowlist can be reviewed.");
        }
        if (value == null) {
            throw new IllegalArgumentException("MySQL JDBC property '" + key + "' has null value");
        }
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '&' || c == '=' || Character.isISOControl(c)) {
                throw new IllegalArgumentException("MySQL JDBC property '" + key
                    + "' contains a forbidden character (control char, '&', or '='). "
                    + "Values must not smuggle additional URL parameters.");
            }
        }
    }

    /**
     * Validates a pool size configuration value.
     *
     * @param value The configured value
     * @param name The configuration key name (for logging)
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @param defaultValue Default value to use if invalid
     * @return Valid pool size value
     */
    int validatePoolSize(int value, String name, int min, int max, int defaultValue) {
        if (value < min || value > max) {
            logger.warning(String.format(
                "Invalid MySQL pool config '%s': %d (valid range: %d-%d). Using default: %d",
                name, value, min, max, defaultValue));
            return defaultValue;
        }
        return value;
    }

    /**
     * Validates a timeout configuration value.
     *
     * @param value The configured value in milliseconds
     * @param name The configuration key name (for logging)
     * @param min Minimum allowed value (HikariCP requirement)
     * @param defaultValue Default value to use if invalid
     * @return Valid timeout value
     */
    long validateTimeout(long value, String name, long min, long defaultValue) {
        if (value < min && value != 0) {
            logger.warning(String.format(
                "Invalid MySQL pool config '%s': %d ms (minimum: %d ms or 0 to disable). Using default: %d ms",
                name, value, min, defaultValue));
            return defaultValue;
        }
        return value;
    }

    /**
     * Initializes MySQL database connection with HikariCP pooling.
     */
    private void initializeMySQL() throws SQLException {
        final String host = mysqlConfig.getString("host", "localhost");
        final int port = mysqlConfig.getInt("port", 3306);
        final String database = mysqlConfig.getString("database", "stormtrooperx");
        final String username = mysqlConfig.getString("username", "root");
        final String password = mysqlConfig.getString("password", "");

        validateMySQLParameters(host, port, database, username);

        final StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);

        // Each property is allowlist-checked and URL-encoded so a value cannot
        // smuggle extra parameters. See validateMySQLProperty.
        final ConfigurationSection properties = mysqlConfig.getConfigurationSection("properties");
        if (properties != null && !properties.getKeys(false).isEmpty()) {
            jdbcUrl.append("?");
            boolean first = true;
            for (String key : properties.getKeys(false)) {
                final String value = properties.getString(key);
                validateMySQLProperty(key, value);
                if (!first) {
                    jdbcUrl.append("&");
                }
                jdbcUrl.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                first = false;
            }
        }

        final HikariConfig config = new HikariConfig();
        // Explicit driver class so Hikari loads the bundled Connector/J instead
        // of falling back through DriverManager to whatever MySQL driver the
        // server happens to ship. The string constant is rewritten to the
        // relocated class name by the shade plugin at package time.
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);

        final ConfigurationSection poolConfig = mysqlConfig.getConfigurationSection("pool");
        if (poolConfig != null) {
            final int maxPoolSize = validatePoolSize(
                poolConfig.getInt("maximum-pool-size", 10), "maximum-pool-size", 1, 100, 10);
            config.setMaximumPoolSize(maxPoolSize);

            final int minIdle = validatePoolSize(
                poolConfig.getInt("minimum-idle", 2), "minimum-idle", 0, maxPoolSize, 2);
            config.setMinimumIdle(minIdle);

            config.setConnectionTimeout(validateTimeout(
                poolConfig.getLong("connection-timeout", 30000), "connection-timeout", 250, 30000));
            config.setIdleTimeout(validateTimeout(
                poolConfig.getLong("idle-timeout", 600000), "idle-timeout", 10000, 600000));
            config.setMaxLifetime(validateTimeout(
                poolConfig.getLong("max-lifetime", 1800000), "max-lifetime", 30000, 1800000));
        }

        // Defense-in-depth: explicitly disable Connector/J flags with RCE /
        // file-read / credential-leak history so an upstream default change
        // can't silently re-enable them. The allowlist already blocks these
        // keys from the URL; this is the belt to that suspenders.
        config.addDataSourceProperty("allowLoadLocalInfile", "false");
        config.addDataSourceProperty("allowUrlInLocalInfile", "false");
        config.addDataSourceProperty("autoDeserialize", "false");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "false");

        // Connector/J + HikariCP performance settings (HikariCP wiki:
        // MySQL-Configuration) — statement caching, fewer round-trips, batch rewrite.
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        hikariDataSource = new HikariDataSource(config);
    }

    /**
     * Gets a connection from either H2 or MySQL pool.
     */
    private Connection getConnection() throws SQLException {
        if (isH2) {
            return h2Connection;
        } else {
            return hikariDataSource.getConnection();
        }
    }

    /**
     * Closes a connection. For H2, does nothing (single connection). For MySQL, returns to pool.
     */
    private void closeConnection(Connection connection) {
        if (!isH2 && connection != null) {
            try {
                connection.close(); // Returns connection to HikariCP pool
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close MySQL connection", e);
            }
        }
        // For H2, do nothing - we keep the single connection open
    }

    /**
     * Creates the necessary database tables.
     */
    private void createTables() throws SQLException {
        final String createTableSQL = "CREATE TABLE IF NOT EXISTS player_optouts (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "opted_out BOOLEAN NOT NULL DEFAULT TRUE, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        Connection connection = null;
        try {
            connection = getConnection();
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTableSQL);
            }
        } finally {
            closeConnection(connection);
        }
    }

    /**
     * Validates that the UUID is not null and the database connection is initialized.
     *
     * @param playerUUID Player's UUID to validate
     * @return true if validation passes, false otherwise
     */
    private boolean validateDatabaseOperation(UUID playerUUID) {
        if (playerUUID == null) {
            logger.warning("Attempted database operation with null UUID");
            return false;
        }

        if (isH2 && h2Connection == null) {
            logger.warning("H2 database connection not initialized");
            return false;
        }

        if (!isH2 && hikariDataSource == null) {
            logger.warning("MySQL database connection pool not initialized");
            return false;
        }

        return true;
    }

    /**
     * Checks if a player has opted out.
     *
     * @param playerUUID Player's UUID
     * @return true if opted out, false otherwise
     */
    public boolean isOptedOut(UUID playerUUID) {
        if (!validateDatabaseOperation(playerUUID)) {
            return false;
        }
        if (isH2) {
            synchronized (h2Lock) {
                return isOptedOutInternal(playerUUID);
            }
        }
        return isOptedOutInternal(playerUUID);
    }

    private boolean isOptedOutInternal(UUID playerUUID) {
        final String query = "SELECT opted_out FROM player_optouts WHERE uuid = ?";

        Connection connection = null;
        try {
            connection = getConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerUUID.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getBoolean("opted_out");
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to check opt-out status for " + playerUUID, e);
        } finally {
            closeConnection(connection);
        }

        return false; // Default to not opted out
    }

    /**
     * Sets a player's opt-out status.
     *
     * @param playerUUID Player's UUID
     * @param optedOut Whether the player is opted out
     */
    public void setOptOut(UUID playerUUID, boolean optedOut) {
        if (!validateDatabaseOperation(playerUUID)) {
            return;
        }
        if (isH2) {
            synchronized (h2Lock) {
                setOptOutInternal(playerUUID, optedOut);
            }
            return;
        }
        setOptOutInternal(playerUUID, optedOut);
    }

    private void setOptOutInternal(UUID playerUUID, boolean optedOut) {
        // Upsert syntax differs: H2 MERGE vs MySQL INSERT ... ON DUPLICATE KEY.
        final String upsertSQL;
        if (isH2) {
            upsertSQL = "MERGE INTO player_optouts (uuid, opted_out, updated_at) KEY(uuid) VALUES (?, ?, CURRENT_TIMESTAMP)";
        } else {
            upsertSQL = "INSERT INTO player_optouts (uuid, opted_out, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE opted_out = VALUES(opted_out), updated_at = CURRENT_TIMESTAMP";
        }

        Connection connection = null;
        try {
            connection = getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsertSQL)) {
                statement.setString(1, playerUUID.toString());
                statement.setBoolean(2, optedOut);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to set opt-out status for " + playerUUID, e);
        } finally {
            closeConnection(connection);
        }
    }

    /**
     * Toggles a player's opt-out status.
     *
     * @param playerUUID Player's UUID
     * @return The new opt-out status
     */
    public boolean toggleOptOut(UUID playerUUID) {
        if (!validateDatabaseOperation(playerUUID)) {
            return false;
        }

        final boolean currentStatus = isOptedOut(playerUUID);
        final boolean newStatus = !currentStatus;
        setOptOut(playerUUID, newStatus);
        return newStatus;
    }

    /**
     * Closes the database connection or pool.
     */
    public void close() {
        if (isH2 && h2Connection != null) {
            try {
                h2Connection.close();
                logger.info("H2 database connection closed");
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close H2 database connection", e);
            }
        } else if (!isH2 && hikariDataSource != null) {
            hikariDataSource.close();
            logger.info("MySQL connection pool closed");
        }
    }
}
