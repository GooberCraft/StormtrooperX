package com.goobercraft.stormtrooperx;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityConfigLoadingTest {

    private StormtrooperX plugin;
    private FileConfiguration mockConfig;
    private Map<EntityType, Object> entityConfigs;
    private Method loadEntityConfigDirect;
    private Method loadEntityConfigVersioned;
    private Method displayEntityStatusDirect;
    private Field accuracyField;
    private Field enabledField;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(StormtrooperX.class, CALLS_REAL_METHODS);

        // Inject a real logger — loadEntityConfig logs at info and warning levels
        Field loggerField = StormtrooperX.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(plugin, Logger.getLogger("EntityConfigLoadingTest"));

        // Inject empty entityConfigs map — field initializer is skipped by Mockito
        Field entityConfigsField = StormtrooperX.class.getDeclaredField("entityConfigs");
        entityConfigsField.setAccessible(true);
        entityConfigs = new EnumMap<>(EntityType.class);
        entityConfigsField.set(plugin, entityConfigs);

        // Stub getConfig() — must use doReturn because CALLS_REAL_METHODS would
        // otherwise invoke the real (NPE-prone) getConfig() during stub registration
        mockConfig = mock(FileConfiguration.class);
        doReturn(mockConfig).when(plugin).getConfig();

        // Resolve private methods
        loadEntityConfigDirect = StormtrooperX.class.getDeclaredMethod(
                "loadEntityConfig", String.class, EntityType.class);
        loadEntityConfigDirect.setAccessible(true);

        loadEntityConfigVersioned = StormtrooperX.class.getDeclaredMethod(
                "loadEntityConfig", String.class, String.class, String.class);
        loadEntityConfigVersioned.setAccessible(true);

        displayEntityStatusDirect = StormtrooperX.class.getDeclaredMethod(
                "displayEntityStatus", CommandSender.class, EntityType.class, String.class);
        displayEntityStatusDirect.setAccessible(true);

        // Resolve EntityConfig fields for assertions
        Class<?> entityConfigClass = null;
        for (Class<?> inner : StormtrooperX.class.getDeclaredClasses()) {
            if ("EntityConfig".equals(inner.getSimpleName())) {
                entityConfigClass = inner;
                break;
            }
        }
        accuracyField = entityConfigClass.getDeclaredField("accuracy");
        accuracyField.setAccessible(true);
        enabledField = entityConfigClass.getDeclaredField("enabled");
        enabledField.setAccessible(true);
    }

    private double getAccuracy(Object entityConfig) throws Exception {
        return (double) accuracyField.get(entityConfig);
    }

    private boolean getEnabled(Object entityConfig) throws Exception {
        return (boolean) enabledField.get(entityConfig);
    }

    // -------------------------------------------------------------------------
    // loadEntityConfig(String, EntityType) — direct/non-version-gated variant
    // Used for Skeleton and Stray (mobs guaranteed to exist on the 1.18 baseline)
    // -------------------------------------------------------------------------

    @Test
    void testLoadDirect_pathMissing_doesNotAddEntry() throws Exception {
        when(mockConfig.contains("entities.skeleton")).thenReturn(false);

        loadEntityConfigDirect.invoke(plugin, "skeleton", EntityType.SKELETON);

        assertTrue(entityConfigs.isEmpty(), "No config path → no entry added");
    }

    @Test
    void testLoadDirect_enabledWithValidAccuracy_addsEntry() throws Exception {
        when(mockConfig.contains("entities.skeleton")).thenReturn(true);
        when(mockConfig.getBoolean("entities.skeleton.enabled", true)).thenReturn(true);
        when(mockConfig.getDouble("entities.skeleton.accuracy", 0.7)).thenReturn(0.5);

        loadEntityConfigDirect.invoke(plugin, "skeleton", EntityType.SKELETON);

        Object cfg = entityConfigs.get(EntityType.SKELETON);
        assertNotNull(cfg, "Enabled entity must be added to map");
        assertEquals(0.5, getAccuracy(cfg), 1e-10);
        assertTrue(getEnabled(cfg));
    }

    @Test
    void testLoadDirect_disabled_doesNotAddEntry() throws Exception {
        when(mockConfig.contains("entities.skeleton")).thenReturn(true);
        when(mockConfig.getBoolean("entities.skeleton.enabled", true)).thenReturn(false);
        when(mockConfig.getDouble("entities.skeleton.accuracy", 0.7)).thenReturn(0.7);

        loadEntityConfigDirect.invoke(plugin, "skeleton", EntityType.SKELETON);

        assertNull(entityConfigs.get(EntityType.SKELETON),
                "Disabled entity must not be added to map");
    }

    @Test
    void testLoadDirect_outOfRangeAccuracy_isClampedAtConstruction() throws Exception {
        when(mockConfig.contains("entities.skeleton")).thenReturn(true);
        when(mockConfig.getBoolean("entities.skeleton.enabled", true)).thenReturn(true);
        when(mockConfig.getDouble("entities.skeleton.accuracy", 0.7)).thenReturn(2.5);

        loadEntityConfigDirect.invoke(plugin, "skeleton", EntityType.SKELETON);

        Object cfg = entityConfigs.get(EntityType.SKELETON);
        assertNotNull(cfg);
        assertEquals(1.0, getAccuracy(cfg), 1e-10,
                "Out-of-range accuracy (2.5) must be clamped to 1.0");
    }

    // -------------------------------------------------------------------------
    // loadEntityConfig(String, String, String) — version-gated variant
    // Used for Bogged (1.21+), Parched (1.21.11+), Pillager (1.14+), Piglin (1.16+)
    // -------------------------------------------------------------------------

    @Test
    void testLoadVersioned_pathMissing_doesNotAddEntry() throws Exception {
        when(mockConfig.contains("entities.bogged")).thenReturn(false);

        loadEntityConfigVersioned.invoke(plugin, "bogged", "BOGGED", "1.21+");

        assertTrue(entityConfigs.isEmpty());
    }

    @Test
    void testLoadVersioned_disabled_earlyReturnsBeforeEntityTypeLookup() throws Exception {
        when(mockConfig.contains("entities.bogged")).thenReturn(true);
        when(mockConfig.getBoolean("entities.bogged.enabled", true)).thenReturn(false);

        loadEntityConfigVersioned.invoke(plugin, "bogged", "BOGGED", "1.21+");

        assertTrue(entityConfigs.isEmpty(),
                "Disabled version-gated entity must not be added");
        // Disabled path returns before reading accuracy or attempting EntityType lookup
        verify(mockConfig, never()).getDouble(anyString(), anyDouble());
    }

    @Test
    void testLoadVersioned_validEntityType_addsEntry() throws Exception {
        when(mockConfig.contains("entities.pillager")).thenReturn(true);
        when(mockConfig.getBoolean("entities.pillager.enabled", true)).thenReturn(true);
        when(mockConfig.getDouble("entities.pillager.accuracy", 0.7)).thenReturn(0.8);

        loadEntityConfigVersioned.invoke(plugin, "pillager", "PILLAGER", "1.14+");

        Object cfg = entityConfigs.get(EntityType.PILLAGER);
        assertNotNull(cfg, "Valid EntityType name must result in an entry");
        assertEquals(0.8, getAccuracy(cfg), 1e-10);
        assertTrue(getEnabled(cfg));
    }

    @Test
    void testLoadVersioned_unknownEntityType_silentlySkips() throws Exception {
        when(mockConfig.contains("entities.fictional")).thenReturn(true);
        when(mockConfig.getBoolean("entities.fictional.enabled", true)).thenReturn(true);

        // This is the same path Parched takes on a 1.21.10 server: EntityType.valueOf()
        // throws IllegalArgumentException, which must be caught and logged.
        loadEntityConfigVersioned.invoke(plugin, "fictional", "NOT_A_REAL_ENTITY_TYPE_XYZ", "9.99+");

        assertTrue(entityConfigs.isEmpty(),
                "Unknown EntityType must result in graceful skip, not exception");
    }

    @Test
    void testLoadVersioned_outOfRangeNegativeAccuracy_isClampedToZero() throws Exception {
        when(mockConfig.contains("entities.pillager")).thenReturn(true);
        when(mockConfig.getBoolean("entities.pillager.enabled", true)).thenReturn(true);
        when(mockConfig.getDouble("entities.pillager.accuracy", 0.7)).thenReturn(-0.5);

        loadEntityConfigVersioned.invoke(plugin, "pillager", "PILLAGER", "1.14+");

        Object cfg = entityConfigs.get(EntityType.PILLAGER);
        assertNotNull(cfg);
        assertEquals(0.0, getAccuracy(cfg), 1e-10,
                "Negative accuracy (-0.5) must be clamped to 0.0");
    }

    // -------------------------------------------------------------------------
    // displayEntityStatus(CommandSender, EntityType, String) — disabled branch
    // -------------------------------------------------------------------------

    @Test
    void testDisplayEntityStatus_entityNotConfigured_showsDisabled() throws Exception {
        CommandSender sender = mock(CommandSender.class);
        // entityConfigs is empty — no entry for SKELETON, so config is null
        // and the else branch (Disabled) is taken.

        displayEntityStatusDirect.invoke(plugin, sender, EntityType.SKELETON, "Skeleton");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).sendMessage(captor.capture());
        assertTrue(captor.getValue().contains("Disabled"),
                "Message must indicate Disabled when entity has no config entry");
        assertTrue(captor.getValue().contains("Skeleton"),
                "Message must include the display name");
    }
}
