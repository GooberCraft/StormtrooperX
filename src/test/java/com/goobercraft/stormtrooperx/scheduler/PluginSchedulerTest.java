package com.goobercraft.stormtrooperx.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the legacy scheduler implementation and the
 * {@link PluginScheduler#create(Plugin)} factory.
 *
 * <p>The Folia-backed implementation is not exercised here because Folia
 * classes are not on the test classpath; on a non-Folia classpath the factory
 * deterministically returns the legacy implementation.</p>
 */
@ExtendWith(MockitoExtension.class)
class PluginSchedulerTest {

    @Mock
    private Plugin plugin;

    @Mock
    private BukkitScheduler bukkitScheduler;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("PluginSchedulerTest"));
    }

    @Test
    void testFactory_nullPlugin_throws() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> PluginScheduler.create(null));
        assertEquals("plugin cannot be null", ex.getMessage());
    }

    @Test
    void testFactory_withoutFolia_returnsLegacy() {
        // RegionizedServer is not on the test classpath, so the factory must
        // pick the legacy implementation deterministically.
        final PluginScheduler scheduler = PluginScheduler.create(plugin);
        assertNotNull(scheduler);
        assertEquals("LegacyBukkitScheduler", scheduler.getClass().getSimpleName());
    }

    @Test
    void testLegacyConstructor_nullPlugin_throws() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> new LegacyBukkitScheduler(null));
        assertEquals("plugin cannot be null", ex.getMessage());
    }

    @Test
    void testLegacyRunAsync_nullTask_throws() {
        final LegacyBukkitScheduler scheduler = new LegacyBukkitScheduler(plugin);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> scheduler.runAsync(null));
        assertEquals("task cannot be null", ex.getMessage());
    }

    @Test
    void testLegacyRunGlobal_nullTask_throws() {
        final LegacyBukkitScheduler scheduler = new LegacyBukkitScheduler(plugin);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> scheduler.runGlobal(null));
        assertEquals("task cannot be null", ex.getMessage());
    }

    @Test
    void testLegacyRunAsync_delegatesToBukkit() {
        try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(bukkitScheduler);
            final LegacyBukkitScheduler scheduler = new LegacyBukkitScheduler(plugin);
            final Runnable task = () -> { };
            scheduler.runAsync(task);
            verify(bukkitScheduler).runTaskAsynchronously(plugin, task);
        }
    }

    @Test
    void testLegacyRunGlobal_delegatesToBukkit() {
        try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(bukkitScheduler);
            final LegacyBukkitScheduler scheduler = new LegacyBukkitScheduler(plugin);
            final Runnable task = () -> { };
            scheduler.runGlobal(task);
            verify(bukkitScheduler).runTask(plugin, task);
        }
    }
}
