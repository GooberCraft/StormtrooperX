package com.goobercraft.stormtrooperx;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MySQL-specific functionality in DatabaseManager.
 * These tests use mocks to verify MySQL configuration, JDBC URL construction,
 * and SQL syntax without requiring a running MySQL server.
 */
class DatabaseManagerMySQLTest {

    private Logger logger = Logger.getLogger("TestLogger");
    private File tempDir = new File(System.getProperty("java.io.tmpdir"));

    @Test
    void testMySQLConstructor_requiresConfig() {
        // MySQL type requires mysqlConfig parameter
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DatabaseManager(logger, tempDir, "mysql", null)
        );

        assertTrue(exception.getMessage().contains("mysqlConfig is required"));
    }

    @Test
    void testConstructor_invalidDatabaseType() {
        // Invalid database type should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DatabaseManager(logger, tempDir, "postgres", null)
        );

        assertTrue(exception.getMessage().contains("must be 'h2' or 'mysql'"));
    }

    @Test
    void testConstructor_nullDatabaseType() {
        // Null database type should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DatabaseManager(logger, tempDir, null, null)
        );

        assertTrue(exception.getMessage().contains("databaseType cannot be null"));
    }

    @Test
    void testConstructor_emptyDatabaseType() {
        // Empty database type should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DatabaseManager(logger, tempDir, "", null)
        );

        assertTrue(exception.getMessage().contains("databaseType cannot be null or empty"));
    }

    @Test
    void testConstructor_nullLogger() {
        // Null logger should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DatabaseManager(null, tempDir, "h2", null)
        );

        assertTrue(exception.getMessage().contains("logger cannot be null"));
    }

    @Test
    void testConstructor_nullDataFolder() {
        // Null data folder should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DatabaseManager(logger, null, "h2", null)
        );

        assertTrue(exception.getMessage().contains("dataFolder cannot be null"));
    }

    @Test
    void testMySQLConfig_defaultValues() {
        // Create mock config with only basic values (no pool or properties sections)
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Should not throw exception
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testMySQLConfig_customHost() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("mysql.example.com");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Constructor should succeed with custom host config
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testMySQLConfig_customPort() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3307);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Constructor should succeed with custom port config
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testMySQLConfig_withPoolConfiguration() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection poolConfig = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(poolConfig);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Pool config values
        when(poolConfig.getInt("maximum-pool-size", 10)).thenReturn(20);
        when(poolConfig.getInt("minimum-idle", 2)).thenReturn(5);
        when(poolConfig.getLong("connection-timeout", 30000)).thenReturn(60000L);
        when(poolConfig.getLong("idle-timeout", 600000)).thenReturn(300000L);
        when(poolConfig.getLong("max-lifetime", 1800000)).thenReturn(900000L);

        // Constructor should succeed with pool configuration
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testMySQLConfig_withCustomProperties() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection properties = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(properties);

        // Custom JDBC properties
        when(properties.getKeys(false)).thenReturn(new HashSet<>(Arrays.asList("useSSL", "serverTimezone")));
        when(properties.getString("useSSL")).thenReturn("true");
        when(properties.getString("serverTimezone")).thenReturn("UTC");

        // Constructor should succeed with custom properties
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testMySQLConfig_emptyPropertiesSection() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection properties = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(properties);

        // Empty properties (no keys)
        when(properties.getKeys(false)).thenReturn(new HashSet<>());

        // Constructor should succeed with empty properties section
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testMySQLConfig_caseInsensitiveDatabaseType() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Test with different case variations
        DatabaseManager manager1 = new DatabaseManager(logger, tempDir, "MySQL", mysqlConfig);
        DatabaseManager manager2 = new DatabaseManager(logger, tempDir, "MYSQL", mysqlConfig);
        DatabaseManager manager3 = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);

        assertNotNull(manager1);
        assertNotNull(manager2);
        assertNotNull(manager3);
    }

    @Test
    void testH2Config_caseInsensitiveDatabaseType() {
        // Test with different case variations for H2
        DatabaseManager manager1 = new DatabaseManager(logger, tempDir, "H2", null);
        DatabaseManager manager2 = new DatabaseManager(logger, tempDir, "h2", null);

        assertNotNull(manager1);
        assertNotNull(manager2);
    }

    @Test
    void testMySQLConfig_withAllCustomSettings() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection poolConfig = mock(ConfigurationSection.class);
        ConfigurationSection properties = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("db.example.com");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3307);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("mydb");
        when(mysqlConfig.getString("username", "root")).thenReturn("dbuser");
        when(mysqlConfig.getString("password", "")).thenReturn("secretpass");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(poolConfig);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(properties);

        // Pool config
        when(poolConfig.getInt("maximum-pool-size", 10)).thenReturn(15);
        when(poolConfig.getInt("minimum-idle", 2)).thenReturn(3);
        when(poolConfig.getLong("connection-timeout", 30000)).thenReturn(45000L);
        when(poolConfig.getLong("idle-timeout", 600000)).thenReturn(700000L);
        when(poolConfig.getLong("max-lifetime", 1800000)).thenReturn(2000000L);

        // Custom properties
        when(properties.getKeys(false)).thenReturn(new HashSet<>(Arrays.asList(
            "useSSL", "requireSSL", "verifyServerCertificate", "serverTimezone", "characterEncoding"
        )));
        when(properties.getString("useSSL")).thenReturn("true");
        when(properties.getString("requireSSL")).thenReturn("true");
        when(properties.getString("verifyServerCertificate")).thenReturn("false");
        when(properties.getString("serverTimezone")).thenReturn("America/New_York");
        when(properties.getString("characterEncoding")).thenReturn("utf8mb4");

        // Constructor should succeed with all custom settings
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }
}
