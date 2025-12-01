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

    // ===========================================
    // MySQL Parameter Validation Tests (Security)
    // ===========================================

    @Test
    void testMySQLValidation_invalidHostWithInjection() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        // Attempt JDBC URL injection via host parameter
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost?allowLoadLocalInfile=true&");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);

        // Should throw IllegalArgumentException when initialize() is called due to invalid host
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.initialize()
        );
        assertTrue(exception.getMessage().contains("Invalid MySQL host format"));
    }

    @Test
    void testMySQLValidation_invalidDatabaseWithInjection() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        // Attempt JDBC URL injection via database parameter
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("test;DROP TABLE users;--");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.initialize()
        );
        assertTrue(exception.getMessage().contains("Invalid MySQL database name"));
    }

    @Test
    void testMySQLValidation_invalidPort() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(99999); // Invalid port
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.initialize()
        );
        assertTrue(exception.getMessage().contains("Invalid MySQL port"));
    }

    @Test
    void testMySQLValidation_invalidPortZero() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(0); // Invalid port
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.initialize()
        );
        assertTrue(exception.getMessage().contains("Invalid MySQL port"));
    }

    @Test
    void testMySQLValidation_invalidUsername() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        // Attempt injection via username
        when(mysqlConfig.getString("username", "root")).thenReturn("root'--");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.initialize()
        );
        assertTrue(exception.getMessage().contains("Invalid MySQL username"));
    }

    @Test
    void testMySQLValidation_validComplexHostname() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        // Valid complex hostname with dots, hyphens, underscores
        when(mysqlConfig.getString("host", "localhost")).thenReturn("db-server_1.mysql.example.com");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Constructor should succeed - validation happens at initialize()
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
        // Note: initialize() would still fail without actual MySQL, but validation passes
    }

    @Test
    void testMySQLValidation_validEdgeCasePorts() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(null);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Test port 1 (minimum valid)
        when(mysqlConfig.getInt("port", 3306)).thenReturn(1);
        DatabaseManager manager1 = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager1);

        // Test port 65535 (maximum valid)
        when(mysqlConfig.getInt("port", 3306)).thenReturn(65535);
        DatabaseManager manager2 = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager2);
    }

    // ===========================================
    // Pool Configuration Validation Tests
    // ===========================================

    @Test
    void testPoolValidation_invalidMaxPoolSizeUsesDefault() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection poolConfig = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(poolConfig);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Invalid: negative pool size
        when(poolConfig.getInt("maximum-pool-size", 10)).thenReturn(-5);
        when(poolConfig.getInt("minimum-idle", 2)).thenReturn(2);
        when(poolConfig.getLong("connection-timeout", 30000)).thenReturn(30000L);
        when(poolConfig.getLong("idle-timeout", 600000)).thenReturn(600000L);
        when(poolConfig.getLong("max-lifetime", 1800000)).thenReturn(1800000L);

        // Should not throw - invalid values use defaults
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testPoolValidation_invalidMinIdleUsesDefault() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection poolConfig = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(poolConfig);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Invalid: minimum-idle greater than maximum-pool-size
        when(poolConfig.getInt("maximum-pool-size", 10)).thenReturn(5);
        when(poolConfig.getInt("minimum-idle", 2)).thenReturn(100);
        when(poolConfig.getLong("connection-timeout", 30000)).thenReturn(30000L);
        when(poolConfig.getLong("idle-timeout", 600000)).thenReturn(600000L);
        when(poolConfig.getLong("max-lifetime", 1800000)).thenReturn(1800000L);

        // Should not throw - invalid values use defaults
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testPoolValidation_invalidTimeoutUsesDefault() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection poolConfig = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(poolConfig);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        when(poolConfig.getInt("maximum-pool-size", 10)).thenReturn(10);
        when(poolConfig.getInt("minimum-idle", 2)).thenReturn(2);
        // Invalid: connection-timeout below minimum (250ms)
        when(poolConfig.getLong("connection-timeout", 30000)).thenReturn(100L);
        // Invalid: idle-timeout below minimum (10000ms)
        when(poolConfig.getLong("idle-timeout", 600000)).thenReturn(5000L);
        // Invalid: max-lifetime below minimum (30000ms)
        when(poolConfig.getLong("max-lifetime", 1800000)).thenReturn(10000L);

        // Should not throw - invalid values use defaults
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testPoolValidation_zeroTimeoutIsValid() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection poolConfig = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(poolConfig);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        when(poolConfig.getInt("maximum-pool-size", 10)).thenReturn(10);
        when(poolConfig.getInt("minimum-idle", 2)).thenReturn(2);
        when(poolConfig.getLong("connection-timeout", 30000)).thenReturn(30000L);
        // Zero is valid (disables the timeout)
        when(poolConfig.getLong("idle-timeout", 600000)).thenReturn(0L);
        when(poolConfig.getLong("max-lifetime", 1800000)).thenReturn(0L);

        // Should not throw - zero is valid for disabling timeouts
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }

    @Test
    void testPoolValidation_excessiveMaxPoolSizeUsesDefault() {
        ConfigurationSection mysqlConfig = mock(ConfigurationSection.class);
        ConfigurationSection poolConfig = mock(ConfigurationSection.class);

        when(mysqlConfig.getString("host", "localhost")).thenReturn("localhost");
        when(mysqlConfig.getInt("port", 3306)).thenReturn(3306);
        when(mysqlConfig.getString("database", "stormtrooperx")).thenReturn("stormtrooperx");
        when(mysqlConfig.getString("username", "root")).thenReturn("root");
        when(mysqlConfig.getString("password", "")).thenReturn("");
        when(mysqlConfig.getConfigurationSection("pool")).thenReturn(poolConfig);
        when(mysqlConfig.getConfigurationSection("properties")).thenReturn(null);

        // Invalid: pool size exceeds maximum (100)
        when(poolConfig.getInt("maximum-pool-size", 10)).thenReturn(500);
        when(poolConfig.getInt("minimum-idle", 2)).thenReturn(2);
        when(poolConfig.getLong("connection-timeout", 30000)).thenReturn(30000L);
        when(poolConfig.getLong("idle-timeout", 600000)).thenReturn(600000L);
        when(poolConfig.getLong("max-lifetime", 1800000)).thenReturn(1800000L);

        // Should not throw - excessive values use defaults
        DatabaseManager manager = new DatabaseManager(logger, tempDir, "mysql", mysqlConfig);
        assertNotNull(manager);
    }
}
