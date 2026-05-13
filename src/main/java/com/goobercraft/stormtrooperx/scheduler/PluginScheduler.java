package com.goobercraft.stormtrooperx.scheduler;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

/**
 * Abstraction over Bukkit's task scheduler that also supports Folia.
 *
 * <p>On Spigot/Paper, calls are routed through the legacy {@code BukkitScheduler}.
 * On Folia, where the legacy scheduler's synchronous methods throw
 * {@code UnsupportedOperationException}, calls are routed through Folia's
 * {@code AsyncScheduler} and {@code GlobalRegionScheduler} via reflection so the
 * plugin remains compilable against the Spigot API.</p>
 *
 * <p>The correct implementation is selected once at plugin enable via
 * {@link #create(Plugin)} and shared across collaborators.</p>
 */
public interface PluginScheduler {

    /**
     * Schedules a task to run asynchronously, off any region/main thread.
     *
     * @param task Task to run (must not be null)
     */
    void runAsync(Runnable task);

    /**
     * Schedules a task to run on the global region thread (the closest analogue
     * to "the main thread" on Folia, and the actual main thread elsewhere).
     *
     * @param task Task to run (must not be null)
     */
    void runGlobal(Runnable task);

    /**
     * Selects and returns the appropriate scheduler implementation for the
     * running server. Folia is detected via the presence of
     * {@code io.papermc.paper.threadedregions.RegionizedServer}; if absent the
     * legacy {@code BukkitScheduler}-based implementation is returned.
     *
     * <p>Emits a single INFO log line at startup describing which path was
     * chosen.</p>
     *
     * @param plugin Plugin instance (must not be null)
     * @return A scheduler appropriate for the host server
     * @throws IllegalArgumentException if plugin is null
     */
    static PluginScheduler create(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("Standard Bukkit scheduler in use");
            return new LegacyBukkitScheduler(plugin);
        }
        // Folia detected. If the reflective adapter cannot initialize (Folia
        // scheduler API moved or was renamed in a future release), fall back
        // to the legacy scheduler rather than failing plugin enable entirely.
        try {
            final PluginScheduler folia = new FoliaScheduler(plugin);
            plugin.getLogger().info("Folia detected, using regional schedulers");
            return folia;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "Folia detected but its scheduler API could not be bound; falling back to legacy scheduler. "
                            + "Async tasks will work; main-thread tasks may throw.", t);
            return new LegacyBukkitScheduler(plugin);
        }
    }
}
