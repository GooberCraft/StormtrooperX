package com.goobercraft.stormtrooperx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for MySQL-specific functionality in {@link DatabaseManager}. Uses
 * mocked {@link ConfigurationSection} instances so no real MySQL server is
 * required; covers constructor validation, JDBC-URL injection guards, and
 * the pool/timeout clamping helpers.
 */
@DisplayName("DatabaseManager — MySQL configuration, validation, and security")
class DatabaseManagerMySQLTest {

    private final Logger logger = Logger.getLogger("DatabaseManagerMySQLTest");
    private final File tempDir = new File(System.getProperty("java.io.tmpdir"));

    /** Builds a mock mysql config with the supplied host/port/db/user/password and no pool/properties sections. */
    private ConfigurationSection mysqlConfig(String host, int port, String database, String username, String password) {
        final ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getString("host", "localhost")).thenReturn(host);
        when(cfg.getInt("port", 3306)).thenReturn(port);
        when(cfg.getString("database", "stormtrooperx")).thenReturn(database);
        when(cfg.getString("username", "root")).thenReturn(username);
        when(cfg.getString("password", "")).thenReturn(password);
        when(cfg.getConfigurationSection("pool")).thenReturn(null);
        when(cfg.getConfigurationSection("properties")).thenReturn(null);
        return cfg;
    }

    /** Default-shape mysql config (the most common test fixture). */
    private ConfigurationSection defaultMysqlConfig() {
        return mysqlConfig("localhost", 3306, "stormtrooperx", "root", "");
    }

    // -------------------------------------------------------------------------
    // Constructor argument validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("constructor argument validation")
    class ConstructorValidation {

        @Test
        @DisplayName("rejects null logger")
        void nullLogger() {
            assertThatThrownBy(() -> new DatabaseManager(null, tempDir, "h2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("logger cannot be null");
        }

        @Test
        @DisplayName("rejects null dataFolder")
        void nullDataFolder() {
            assertThatThrownBy(() -> new DatabaseManager(logger, null, "h2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataFolder cannot be null");
        }

        @Test
        @DisplayName("rejects null databaseType")
        void nullDatabaseType() {
            assertThatThrownBy(() -> new DatabaseManager(logger, tempDir, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("databaseType cannot be null");
        }

        @Test
        @DisplayName("rejects empty databaseType")
        void emptyDatabaseType() {
            assertThatThrownBy(() -> new DatabaseManager(logger, tempDir, "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("databaseType cannot be null or empty");
        }

        @Test
        @DisplayName("rejects unknown databaseType (e.g., 'postgres')")
        void invalidDatabaseType() {
            assertThatThrownBy(() -> new DatabaseManager(logger, tempDir, "postgres", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be 'h2' or 'mysql'");
        }

        @Test
        @DisplayName("rejects mysql type without a config section")
        void mysqlWithoutConfig() {
            assertThatThrownBy(() -> new DatabaseManager(logger, tempDir, "mysql", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mysqlConfig is required");
        }

        @ParameterizedTest(name = "accepts databaseType=[{0}] case-insensitively")
        @ValueSource(strings = {"h2", "H2"})
        void h2CaseInsensitive(String type) {
            assertThat(new DatabaseManager(logger, tempDir, type, null)).isNotNull();
        }

        @ParameterizedTest(name = "accepts databaseType=[{0}] case-insensitively")
        @ValueSource(strings = {"mysql", "MySQL", "MYSQL"})
        void mysqlCaseInsensitive(String type) {
            assertThat(new DatabaseManager(logger, tempDir, type, defaultMysqlConfig())).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // MySQL config — varying shapes are all accepted at construction time
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("MySQL config — accepted shapes")
    class MysqlConfigShapes {

        @Test
        @DisplayName("default values (no pool, no properties)")
        void defaults() {
            assertThat(new DatabaseManager(logger, tempDir, "mysql", defaultMysqlConfig())).isNotNull();
        }

        @Test
        @DisplayName("custom host")
        void customHost() {
            assertThat(new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("mysql.example.com", 3306, "stormtrooperx", "root", ""))).isNotNull();
        }

        @Test
        @DisplayName("custom port")
        void customPort() {
            assertThat(new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("localhost", 3307, "stormtrooperx", "root", ""))).isNotNull();
        }

        @Test
        @DisplayName("complex hostname with dots/hyphens/underscores")
        void complexHostname() {
            assertThat(new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("db-server_1.mysql.example.com", 3306, "stormtrooperx", "root", ""))).isNotNull();
        }

        @ParameterizedTest(name = "edge-case port {0}")
        @ValueSource(ints = {1, 65535})
        void edgeCasePorts(int port) {
            assertThat(new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("localhost", port, "stormtrooperx", "root", ""))).isNotNull();
        }

        @Test
        @DisplayName("with HikariCP pool configuration")
        void withPoolConfig() {
            final ConfigurationSection cfg = defaultMysqlConfig();
            final ConfigurationSection pool = mock(ConfigurationSection.class);
            when(cfg.getConfigurationSection("pool")).thenReturn(pool);
            when(pool.getInt("maximum-pool-size", 10)).thenReturn(20);
            when(pool.getInt("minimum-idle", 2)).thenReturn(5);
            when(pool.getLong("connection-timeout", 30000)).thenReturn(60000L);
            when(pool.getLong("idle-timeout", 600000)).thenReturn(300000L);
            when(pool.getLong("max-lifetime", 1800000)).thenReturn(900000L);

            assertThat(new DatabaseManager(logger, tempDir, "mysql", cfg)).isNotNull();
        }

        @Test
        @DisplayName("with custom JDBC properties (SSL etc.)")
        void withCustomProperties() {
            final ConfigurationSection cfg = defaultMysqlConfig();
            final ConfigurationSection props = mock(ConfigurationSection.class);
            when(cfg.getConfigurationSection("properties")).thenReturn(props);
            when(props.getKeys(false)).thenReturn(new HashSet<>(Arrays.asList("useSSL", "serverTimezone")));
            when(props.getString("useSSL")).thenReturn("true");
            when(props.getString("serverTimezone")).thenReturn("UTC");

            assertThat(new DatabaseManager(logger, tempDir, "mysql", cfg)).isNotNull();
        }

        @Test
        @DisplayName("with empty properties section")
        void emptyPropertiesSection() {
            final ConfigurationSection cfg = defaultMysqlConfig();
            final ConfigurationSection props = mock(ConfigurationSection.class);
            when(cfg.getConfigurationSection("properties")).thenReturn(props);
            when(props.getKeys(false)).thenReturn(new HashSet<>());

            assertThat(new DatabaseManager(logger, tempDir, "mysql", cfg)).isNotNull();
        }

        @Test
        @DisplayName("with all custom settings (host, port, pool, SSL properties)")
        void allCustom() {
            final ConfigurationSection cfg = mysqlConfig("db.example.com", 3307, "mydb", "dbuser", "secretpass");
            final ConfigurationSection pool = mock(ConfigurationSection.class);
            final ConfigurationSection props = mock(ConfigurationSection.class);
            when(cfg.getConfigurationSection("pool")).thenReturn(pool);
            when(cfg.getConfigurationSection("properties")).thenReturn(props);

            when(pool.getInt("maximum-pool-size", 10)).thenReturn(15);
            when(pool.getInt("minimum-idle", 2)).thenReturn(3);
            when(pool.getLong("connection-timeout", 30000)).thenReturn(45000L);
            when(pool.getLong("idle-timeout", 600000)).thenReturn(700000L);
            when(pool.getLong("max-lifetime", 1800000)).thenReturn(2000000L);

            when(props.getKeys(false)).thenReturn(new HashSet<>(Arrays.asList(
                "useSSL", "requireSSL", "verifyServerCertificate", "serverTimezone", "characterEncoding"
            )));
            when(props.getString("useSSL")).thenReturn("true");
            when(props.getString("requireSSL")).thenReturn("true");
            when(props.getString("verifyServerCertificate")).thenReturn("false");
            when(props.getString("serverTimezone")).thenReturn("America/New_York");
            when(props.getString("characterEncoding")).thenReturn("utf8mb4");

            assertThat(new DatabaseManager(logger, tempDir, "mysql", cfg)).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // JDBC URL injection guards — these run at initialize(), not construction
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("MySQL initialize() — URL/SQL injection guards")
    class InjectionGuards {

        @Test
        @DisplayName("rejects host with appended JDBC parameters")
        void invalidHost() {
            final DatabaseManager mgr = new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("localhost?allowLoadLocalInfile=true&", 3306, "stormtrooperx", "root", ""));
            assertThatThrownBy(mgr::initialize)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MySQL host format");
        }

        @Test
        @DisplayName("rejects database name with SQL metacharacters")
        void invalidDatabaseName() {
            final DatabaseManager mgr = new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("localhost", 3306, "test;DROP TABLE users;--", "root", ""));
            assertThatThrownBy(mgr::initialize)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MySQL database name");
        }

        @Test
        @DisplayName("rejects username with SQL injection attempt")
        void invalidUsername() {
            final DatabaseManager mgr = new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("localhost", 3306, "stormtrooperx", "root'--", ""));
            assertThatThrownBy(mgr::initialize)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MySQL username");
        }

        @ParameterizedTest(name = "rejects out-of-range port {0}")
        @ValueSource(ints = {0, 99999})
        void invalidPort(int port) {
            final DatabaseManager mgr = new DatabaseManager(logger, tempDir, "mysql",
                mysqlConfig("localhost", port, "stormtrooperx", "root", ""));
            assertThatThrownBy(mgr::initialize)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MySQL port");
        }
    }

    // -------------------------------------------------------------------------
    // Pool-size validator clamping
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validatePoolSize(value, name, min, max, default)")
    class ValidatePoolSize {

        private final DatabaseManager manager = new DatabaseManager(logger, tempDir, "h2", null);

        @ParameterizedTest(name = "value={0} (in range 1..100) is kept")
        @ValueSource(ints = {1, 10, 100})
        void inRangeValuesArePreserved(int value) {
            assertThat(manager.validatePoolSize(value, "test", 1, 100, 5)).isEqualTo(value);
        }

        @ParameterizedTest(name = "value={0} (out of range 1..100) falls back to default 10")
        @ValueSource(ints = {-5, 0, 101, 500})
        void outOfRangeValuesFallBackToDefault(int value) {
            assertThat(manager.validatePoolSize(value, "maximum-pool-size", 1, 100, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("minimum-idle cannot exceed maximum-pool-size — clamps to default")
        void minIdleAboveMaxFallsBackToDefault() {
            assertThat(manager.validatePoolSize(100, "minimum-idle", 0, 5, 2)).isEqualTo(2);
        }
    }

    // -------------------------------------------------------------------------
    // Timeout validator clamping
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validateTimeout(value, name, min, default)")
    class ValidateTimeout {

        private final DatabaseManager manager = new DatabaseManager(logger, tempDir, "h2", null);

        @ParameterizedTest(name = "value={0} >= min (250) is kept")
        @ValueSource(longs = {250L, 30000L, 60000L})
        void atOrAboveMinimumIsPreserved(long value) {
            assertThat(manager.validateTimeout(value, "test", 250, 30000)).isEqualTo(value);
        }

        @ParameterizedTest(name = "{0} is below {1} -> default {2}")
        @CsvSource({
            "100,  250,    30000",
            "5000, 10000, 600000",
        })
        void belowMinimumReturnsDefault(long value, long min, long defaultValue) {
            assertThat(manager.validateTimeout(value, "test-timeout", min, defaultValue)).isEqualTo(defaultValue);
        }

        @ParameterizedTest(name = "zero is a valid sentinel for {0}")
        @ValueSource(strings = {"idle-timeout", "max-lifetime"})
        void zeroDisablesTimeout(String name) {
            assertThat(manager.validateTimeout(0L, name, 10000, 600000)).isZero();
        }
    }
}
