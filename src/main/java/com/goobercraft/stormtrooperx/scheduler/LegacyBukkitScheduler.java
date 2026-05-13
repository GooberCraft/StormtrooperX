package com.goobercraft.stormtrooperx.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * {@link PluginScheduler} implementation backed by the classic
 * {@code BukkitScheduler}. Used on Spigot and Paper-without-Folia.
 */
final class LegacyBukkitScheduler implements PluginScheduler {

    private final Plugin plugin;

    LegacyBukkitScheduler(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runGlobal(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
