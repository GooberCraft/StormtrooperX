package com.goobercraft.stormtrooperx;

import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
        // a loaded plugin.
        mockConfig = mock(FileConfiguration.class);
        doReturn(mockConfig).when(plugin).getConfig();
        doNothing().when(plugin).saveConfig();
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
}
