package com.goobercraft.stormtrooperx.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * {@link PluginScheduler} implementation that routes through Folia's
 * {@code AsyncScheduler} and {@code GlobalRegionScheduler} via reflection so
 * the plugin can keep its Spigot-API compile dependency.
 *
 * <p>This class is package-private and is only instantiated from
 * {@link PluginScheduler#create(Plugin)} after the {@code RegionizedServer}
 * gate has passed, ensuring its static initializer is never triggered on a
 * non-Folia server.</p>
 */
final class FoliaScheduler implements PluginScheduler {

    private static final Method GET_ASYNC_SCHEDULER;
    private static final Method GET_GLOBAL_REGION_SCHEDULER;
    private static final Method ASYNC_RUN_NOW;
    private static final Method GLOBAL_RUN;

    static {
        try {
            GET_ASYNC_SCHEDULER = Bukkit.class.getMethod("getAsyncScheduler");
            GET_GLOBAL_REGION_SCHEDULER = Bukkit.class.getMethod("getGlobalRegionScheduler");
            final Class<?> asyncSchedulerClass = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            final Class<?> globalSchedulerClass = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            ASYNC_RUN_NOW = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
            GLOBAL_RUN = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Folia detected but its scheduler API is missing or changed", e);
        }
    }

    private final Plugin plugin;

    FoliaScheduler(Plugin plugin) {
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
        try {
            final Object asyncScheduler = GET_ASYNC_SCHEDULER.invoke(null);
            ASYNC_RUN_NOW.invoke(asyncScheduler, plugin, (Consumer<Object>) handle -> task.run());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to dispatch async task via Folia scheduler", e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void runGlobal(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        try {
            final Object globalScheduler = GET_GLOBAL_REGION_SCHEDULER.invoke(null);
            GLOBAL_RUN.invoke(globalScheduler, plugin, (Consumer<Object>) handle -> task.run());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to dispatch global task via Folia scheduler", e);
            throw new IllegalStateException(e);
        }
    }
}
