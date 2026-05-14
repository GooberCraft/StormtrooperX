package com.goobercraft.stormtrooperx;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StormtrooperX — config v2 -> v3 migration")
class ConfigMigrationTest {

    private StormtrooperX plugin;
    private FileConfiguration mockConfig;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(StormtrooperX.class, CALLS_REAL_METHODS);

        // Inject a real logger — migrateConfigToV3() calls logger.info() twice
        // and the field initializer is skipped when Mockito creates the mock
        Field loggerField = StormtrooperX.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(plugin, Logger.getLogger("ConfigMigrationTest"));

        // Stub getConfig() and saveConfig() — the only JavaPlugin methods called.
        // Must use doReturn/doNothing (not when/thenReturn) because CALLS_REAL_METHODS
        // would invoke the real getConfig() during stub registration, which NPEs without
        // a loaded plugin. lenient() because the fixture-based test below overrides
        // getConfig() with a real YamlConfiguration, leaving the setUp stub unused for
        // that single case.
        mockConfig = mock(FileConfiguration.class);
        lenient().doReturn(mockConfig).when(plugin).getConfig();
        lenient().doNothing().when(plugin).saveConfig();
    }

    @Test
    void testMigrateConfigToV3_setsConfigVersionTo3() throws Exception {
        invokeMigrateConfigToV3();

        verify(mockConfig).set("config-version", 3);
    }

    @Test
    void testMigrateConfigToV3_setsDatabaseDefaults() throws Exception {
        invokeMigrateConfigToV3();

        verify(mockConfig).set("database.type", "h2");
        verify(mockConfig).set("database.mysql.host", "localhost");
        verify(mockConfig).set("database.mysql.port", 3306);
        verify(mockConfig).set("database.mysql.database", "stormtrooperx");
        verify(mockConfig).set("database.mysql.username", "root");
        verify(mockConfig).set("database.mysql.password", "");
    }

    @Test
    void testMigrateConfigToV3_setsPoolDefaults() throws Exception {
        invokeMigrateConfigToV3();

        verify(mockConfig).set("database.mysql.pool.maximum-pool-size", 10);
        verify(mockConfig).set("database.mysql.pool.minimum-idle", 2);
        verify(mockConfig).set("database.mysql.pool.connection-timeout", 30000);
        verify(mockConfig).set("database.mysql.pool.idle-timeout", 600000);
        verify(mockConfig).set("database.mysql.pool.max-lifetime", 1800000);
    }

    @Test
    void testMigrateConfigToV3_savesConfig() throws Exception {
        invokeMigrateConfigToV3();

        verify(plugin).saveConfig();
    }

    private void invokeMigrateConfigToV3() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("migrateConfigToV3");
        method.setAccessible(true);
        method.invoke(plugin);
    }

    @Test
    @DisplayName("v2 YAML fixture migrates to a config that matches the v3 fixture's database shape")
    void migratesV2FixtureToV3Shape() throws Exception {
        // Load the v2 fixture as a real YamlConfiguration so we exercise actual ser/de
        // rather than only verifying mock.set() calls.
        final YamlConfiguration realV2;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/fixtures/config-v2.yml"), StandardCharsets.UTF_8)) {
            realV2 = YamlConfiguration.loadConfiguration(reader);
        }
        doReturn(realV2).when(plugin).getConfig();

        invokeMigrateConfigToV3();

        // After migration, the v2 fixture should have grown a database section that matches v3 defaults.
        assertThat(realV2.getInt("config-version")).isEqualTo(3);
        assertThat(realV2.getString("database.type")).isEqualTo("h2");
        assertThat(realV2.getString("database.mysql.host")).isEqualTo("localhost");
        assertThat(realV2.getInt("database.mysql.port")).isEqualTo(3306);
        assertThat(realV2.getInt("database.mysql.pool.maximum-pool-size")).isEqualTo(10);
        // Pre-existing v2 settings are preserved untouched.
        assertThat(realV2.getBoolean("check-for-updates")).isTrue();
        assertThat(realV2.getDouble("entities.skeleton.accuracy")).isEqualTo(0.7);
    }
}
