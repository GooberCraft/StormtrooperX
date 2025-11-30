package com.goobercraft.stormtrooperx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private final File dataFolder;
    private final ConfigurationSection mysqlConfig;

    // H2 connection (single connection, no pooling)
    private Connection h2Connection;

    // MySQL connection pool (HikariCP)
    private HikariDataSource hikariDataSource;

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
        // Defensive: validate constructor parameters
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
        this.databaseType = databaseType.toLowerCase();
        this.mysqlConfig = mysqlConfig;
    }

    /**
     * Initializes the database connection and creates tables.
     */
    public void initialize() {
        try {
            if (databaseType.equals("h2")) {
                initializeH2();
            } else {
                initializeMySQL();
            }

            // Create table if it doesn't exist
            createTables();

            logger.info("Database initialized successfully (" + databaseType.toUpperCase() + ")");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Failed to load database driver", e);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    /**
     * Initializes H2 embedded database connection.
     */
    private void initializeH2() throws ClassNotFoundException, SQLException {
        // Load H2 driver
        Class.forName("org.h2.Driver");

        // Create connection with minimal security options for local embedded database:
        // - FILE_LOCK=SOCKET: Prevents concurrent access issues (not security, but data integrity)
        // - MODE=MySQL: MySQL compatibility mode for familiar syntax
        final File databaseFile = new File(dataFolder, "players");
        final String url = "jdbc:h2:" + databaseFile.getAbsolutePath() + ";MODE=MySQL;FILE_LOCK=SOCKET";
        h2Connection = DriverManager.getConnection(url, "sa", "");
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

        // Build JDBC URL with custom properties
        final StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);

        // Append custom connection properties if provided
        final ConfigurationSection properties = mysqlConfig.getConfigurationSection("properties");
        if (properties != null && !properties.getKeys(false).isEmpty()) {
            jdbcUrl.append("?");
            boolean first = true;
            for (String key : properties.getKeys(false)) {
                if (!first) {
                    jdbcUrl.append("&");
                }
                jdbcUrl.append(key).append("=").append(properties.getString(key));
                first = false;
            }
        }

        // Configure HikariCP
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);

        // Pool configuration
        final ConfigurationSection poolConfig = mysqlConfig.getConfigurationSection("pool");
        if (poolConfig != null) {
            config.setMaximumPoolSize(poolConfig.getInt("maximum-pool-size", 10));
            config.setMinimumIdle(poolConfig.getInt("minimum-idle", 2));
            config.setConnectionTimeout(poolConfig.getLong("connection-timeout", 30000));
            config.setIdleTimeout(poolConfig.getLong("idle-timeout", 600000));
            config.setMaxLifetime(poolConfig.getLong("max-lifetime", 1800000));
        }

        // Connection pool properties
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        hikariDataSource = new HikariDataSource(config);
    }

    /**
     * Gets a connection from either H2 or MySQL pool.
     */
    private Connection getConnection() throws SQLException {
        if (databaseType.equals("h2")) {
            return h2Connection;
        } else {
            return hikariDataSource.getConnection();
        }
    }

    /**
     * Closes a connection. For H2, does nothing (single connection). For MySQL, returns to pool.
     */
    private void closeConnection(Connection connection) {
        if (databaseType.equals("mysql") && connection != null) {
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

        if (databaseType.equals("h2") && h2Connection == null) {
            logger.warning("H2 database connection not initialized");
            return false;
        }

        if (databaseType.equals("mysql") && hikariDataSource == null) {
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

        // Use appropriate upsert syntax for database type
        final String upsertSQL;
        if (databaseType.equals("h2")) {
            // H2 syntax (MySQL mode)
            upsertSQL = "MERGE INTO player_optouts (uuid, opted_out, updated_at) KEY(uuid) VALUES (?, ?, CURRENT_TIMESTAMP)";
        } else {
            // MySQL syntax
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
        if (databaseType.equals("h2") && h2Connection != null) {
            try {
                h2Connection.close();
                logger.info("H2 database connection closed");
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close H2 database connection", e);
            }
        } else if (databaseType.equals("mysql") && hikariDataSource != null) {
            hikariDataSource.close();
            logger.info("MySQL connection pool closed");
        }
    }
}
