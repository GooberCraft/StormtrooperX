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
     * Selects the scheduler implementation for the running server: Folia (detected
     * via {@code io.papermc.paper.threadedregions.RegionizedServer}) or the legacy
     * {@code BukkitScheduler}. Logs one INFO line describing the chosen path.
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
        // Folia detected — fall back to the legacy scheduler if the reflective
        // adapter can't bind (Folia API moved/renamed) rather than failing enable.
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
