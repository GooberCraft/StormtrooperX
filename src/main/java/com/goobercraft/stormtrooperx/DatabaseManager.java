package com.goobercraft.stormtrooperx;

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
 * Manages H2 database operations for player opt-out preferences.
 */
public class DatabaseManager {

    private final Logger logger;
    private final File databaseFile;
    private Connection connection;

    /**
     * Creates a new database manager.
     *
     * @param logger Logger instance
     * @param dataFolder Plugin data folder
     */
    public DatabaseManager(Logger logger, File dataFolder) {
        this.logger = logger;
        this.databaseFile = new File(dataFolder, "players");
    }

    /**
     * Initializes the database connection and creates tables.
     */
    public void initialize() {
        try {
            // Load H2 driver
            Class.forName("org.h2.Driver");

            // Create connection with security options:
            // - FILE_LOCK=SOCKET: Prevents concurrent access from multiple processes
            // - TRACE_LEVEL_FILE=0: Disables trace logging to prevent information leakage
            // - MODE=MySQL: MySQL compatibility mode
            String url = "jdbc:h2:" + databaseFile.getAbsolutePath() + 
                ";MODE=MySQL;FILE_LOCK=SOCKET;TRACE_LEVEL_FILE=0";
            connection = DriverManager.getConnection(url, "sa", "");

            // Create table if it doesn't exist
            createTables();

            logger.info("Database initialized successfully");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Failed to load H2 driver", e);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    /**
     * Creates the necessary database tables.
     */
    private void createTables() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS player_optouts (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "opted_out BOOLEAN NOT NULL DEFAULT TRUE, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        }
    }

    /**
     * Checks if a player has opted out.
     *
     * @param playerUUID Player's UUID
     * @return true if opted out, false otherwise
     */
    public boolean isOptedOut(UUID playerUUID) {
        if (playerUUID == null) {
            logger.warning("Attempted to check opt-out status with null UUID");
            return false;
        }

        if (connection == null) {
            logger.warning("Database connection not initialized");
            return false;
        }

        String query = "SELECT opted_out FROM player_optouts WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("opted_out");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to check opt-out status for " + playerUUID, e);
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
        if (playerUUID == null) {
            logger.warning("Attempted to set opt-out status with null UUID");
            return;
        }

        if (connection == null) {
            logger.warning("Database connection not initialized");
            return;
        }

        String upsertSQL = "MERGE INTO player_optouts (uuid, opted_out, updated_at) " +
                "KEY(uuid) VALUES (?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement statement = connection.prepareStatement(upsertSQL)) {
            statement.setString(1, playerUUID.toString());
            statement.setBoolean(2, optedOut);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to set opt-out status for " + playerUUID, e);
        }
    }

    /**
     * Toggles a player's opt-out status.
     *
     * @param playerUUID Player's UUID
     * @return The new opt-out status
     */
    public boolean toggleOptOut(UUID playerUUID) {
        boolean currentStatus = isOptedOut(playerUUID);
        boolean newStatus = !currentStatus;
        setOptOut(playerUUID, newStatus);
        return newStatus;
    }

    /**
     * Closes the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close database connection", e);
            }
        }
    }
}
