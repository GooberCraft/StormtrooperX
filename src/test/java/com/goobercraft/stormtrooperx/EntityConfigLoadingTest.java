package com.goobercraft.stormtrooperx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.goobercraft.stormtrooperx.support.TestSupport;

/**
 * Tests for {@code StormtrooperX.loadEntityConfig} and
 * {@code displayEntityStatus}. Reaches the private methods via
 * {@link TestSupport#invokePrivate}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StormtrooperX — per-entity config loading + status display")
class EntityConfigLoadingTest {

    private StormtrooperX plugin;
    private FileConfiguration mockConfig;
    private EnumMap<EntityType, StormtrooperX.EntityConfig> entityConfigs;

    @BeforeEach
    void setUp() {
        plugin = mock(StormtrooperX.class, CALLS_REAL_METHODS);

        TestSupport.inject(plugin, "logger", Logger.getLogger("EntityConfigLoadingTest"));
        entityConfigs = new EnumMap<>(EntityType.class);
        TestSupport.inject(plugin, "entityConfigs", entityConfigs);

        // doReturn() is mandatory under CALLS_REAL_METHODS — when/thenReturn would invoke
        // the real getConfig() during stub registration and NPE without a loaded plugin.
        mockConfig = mock(FileConfiguration.class);
        doReturn(mockConfig).when(plugin).getConfig();
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("loadEntityConfig(name, type) — non-version-gated (Skeleton/Stray)")
    class LoadDirect {

        @Test
        @DisplayName("config path missing -> no entry")
        void pathMissing() {
            when(mockConfig.contains("entities.skeleton")).thenReturn(false);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "skeleton", EntityType.SKELETON);

            assertThat(entityConfigs).isEmpty();
        }

        @Test
        @DisplayName("enabled + valid accuracy -> entry with that accuracy is added")
        void enabledValidAccuracy() {
            when(mockConfig.contains("entities.skeleton")).thenReturn(true);
            when(mockConfig.getBoolean("entities.skeleton.enabled", true)).thenReturn(true);
            when(mockConfig.getDouble("entities.skeleton.accuracy", 0.7)).thenReturn(0.5);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "skeleton", EntityType.SKELETON);

            final StormtrooperX.EntityConfig cfg = entityConfigs.get(EntityType.SKELETON);
            assertThat(cfg).isNotNull();
            assertThat(cfg.isEnabled()).isTrue();
            assertThat(cfg.getAccuracy()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("disabled in config -> no entry added")
        void disabled() {
            when(mockConfig.contains("entities.skeleton")).thenReturn(true);
            when(mockConfig.getBoolean("entities.skeleton.enabled", true)).thenReturn(false);
            when(mockConfig.getDouble("entities.skeleton.accuracy", 0.7)).thenReturn(0.7);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "skeleton", EntityType.SKELETON);

            assertThat(entityConfigs).doesNotContainKey(EntityType.SKELETON);
        }

        @Test
        @DisplayName("out-of-range accuracy is clamped to 1.0 at construction time")
        void outOfRangeAccuracyClamps() {
            when(mockConfig.contains("entities.skeleton")).thenReturn(true);
            when(mockConfig.getBoolean("entities.skeleton.enabled", true)).thenReturn(true);
            when(mockConfig.getDouble("entities.skeleton.accuracy", 0.7)).thenReturn(2.5);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "skeleton", EntityType.SKELETON);

            assertThat(entityConfigs.get(EntityType.SKELETON).getAccuracy()).isEqualTo(1.0);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("loadEntityConfig(name, type-string, minVersion) — version-gated mobs")
    class LoadVersioned {

        @Test
        @DisplayName("config path missing -> no entry")
        void pathMissing() {
            when(mockConfig.contains("entities.bogged")).thenReturn(false);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "bogged", "BOGGED", "1.21+");

            assertThat(entityConfigs).isEmpty();
        }

        @Test
        @DisplayName("disabled -> returns before reading accuracy or resolving EntityType")
        void disabledShortCircuits() {
            when(mockConfig.contains("entities.bogged")).thenReturn(true);
            when(mockConfig.getBoolean("entities.bogged.enabled", true)).thenReturn(false);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "bogged", "BOGGED", "1.21+");

            assertThat(entityConfigs).isEmpty();
            verify(mockConfig, never()).getDouble(anyString(), anyDouble());
        }

        @Test
        @DisplayName("known EntityType -> entry added with configured accuracy")
        void knownEntityType() {
            when(mockConfig.contains("entities.pillager")).thenReturn(true);
            when(mockConfig.getBoolean("entities.pillager.enabled", true)).thenReturn(true);
            when(mockConfig.getDouble("entities.pillager.accuracy", 0.7)).thenReturn(0.8);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "pillager", "PILLAGER", "1.14+");

            final StormtrooperX.EntityConfig cfg = entityConfigs.get(EntityType.PILLAGER);
            assertThat(cfg).isNotNull();
            assertThat(cfg.isEnabled()).isTrue();
            assertThat(cfg.getAccuracy()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("unknown EntityType (running on an older MC) -> graceful skip, no exception")
        void unknownEntityType() {
            when(mockConfig.contains("entities.fictional")).thenReturn(true);
            when(mockConfig.getBoolean("entities.fictional.enabled", true)).thenReturn(true);

            // Same path Parched takes on a 1.21.10 server.
            TestSupport.invokePrivate(plugin, "loadEntityConfig", "fictional", "NOT_A_REAL_ENTITY_TYPE_XYZ", "9.99+");

            assertThat(entityConfigs).isEmpty();
        }

        @Test
        @DisplayName("negative accuracy is clamped to 0.0")
        void negativeAccuracyClamps() {
            when(mockConfig.contains("entities.pillager")).thenReturn(true);
            when(mockConfig.getBoolean("entities.pillager.enabled", true)).thenReturn(true);
            when(mockConfig.getDouble("entities.pillager.accuracy", 0.7)).thenReturn(-0.5);

            TestSupport.invokePrivate(plugin, "loadEntityConfig", "pillager", "PILLAGER", "1.14+");

            assertThat(entityConfigs.get(EntityType.PILLAGER).getAccuracy()).isEqualTo(0.0);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("displayEntityStatus — disabled branch")
    class DisplayEntityStatus {

        @Test
        @DisplayName("entity not in map -> sender sees 'Disabled' message")
        void entityNotConfigured() {
            final CommandSender sender = mock(CommandSender.class);

            TestSupport.invokePrivate(plugin, "displayEntityStatus", sender, EntityType.SKELETON, "Skeleton");

            final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("Disabled").contains("Skeleton");
        }
    }
}
