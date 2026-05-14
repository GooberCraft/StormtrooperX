package com.goobercraft.stormtrooperx.support;

import com.goobercraft.stormtrooperx.scheduler.PluginScheduler;

/**
 * {@link PluginScheduler} that runs scheduled tasks inline on the caller
 * thread. Lets tests exercise async code paths deterministically without
 * spinning up Bukkit's real scheduler.
 */
public final class InlinePluginScheduler implements PluginScheduler {

    @Override
    public void runAsync(Runnable task) {
        task.run();
    }

    @Override
    public void runGlobal(Runnable task) {
        task.run();
    }
}
