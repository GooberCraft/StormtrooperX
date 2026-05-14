package com.goobercraft.stormtrooperx;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.goobercraft.stormtrooperx.scheduler.PluginScheduler;

/**
 * Facade over {@link DatabaseManager} backed by an in-memory cache: opt-out
 * status is loaded on player join, evicted on quit, and all DB I/O runs async
 * so the main thread is never blocked.
 *
 * <p>Thread-safe — the cache is a {@link ConcurrentHashMap}-backed {@link Set},
 * so reads are lock-free and safe from any thread.</p>
 */
public class OptOutManager implements Listener {

    private final Logger logger;
    private final DatabaseManager databaseManager;
    private final PluginScheduler scheduler;
    private final Set<UUID> optedOutCache;

    /**
     * Creates a new opt-out manager.
     *
     * @param logger Logger instance (must not be null)
     * @param databaseManager Database manager for persistence (must not be null)
     * @param scheduler Scheduler abstraction for async dispatch (must not be null)
     * @param maxPlayers Maximum number of players on the server (must be positive)
     * @throws IllegalArgumentException if any parameter is null or maxPlayers is not positive
     */
    public OptOutManager(Logger logger, DatabaseManager databaseManager,
                         PluginScheduler scheduler, int maxPlayers) {
        if (logger == null) {
            throw new IllegalArgumentException("logger cannot be null");
        }
        if (databaseManager == null) {
            throw new IllegalArgumentException("databaseManager cannot be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler cannot be null");
        }
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("maxPlayers must be positive, got: " + maxPlayers);
        }

        this.logger = logger;
        this.databaseManager = databaseManager;
        this.scheduler = scheduler;

        // Size for ~25% of max players; floor 16, cap 16384 (misconfigured maxPlayers).
        final int initialCapacity = Math.min(Math.max(16, maxPlayers / 4), 16384);
        this.optedOutCache = Collections.newSetFromMap(new ConcurrentHashMap<>(initialCapacity));

        logger.fine("OptOutManager cache initialized with capacity: " + initialCapacity + " (thread-safe)");
    }

    /**
     * Shuts down the opt-out manager and clears the cache.
     */
    public void shutdown() {
        optedOutCache.clear();
        logger.info("OptOutManager shut down, cache cleared");
    }

    /**
     * Checks if a player has opted out.
     *
     * <p><b>Online-only:</b> consults only the in-memory cache (populated on
     * join, cleared on quit), so an offline player always reads {@code false}
     * regardless of persisted state. Callers needing persisted state should
     * query {@link DatabaseManager#isOptedOut(UUID)}. Lock-free, safe from any
     * thread.</p>
     *
     * @param playerUUID Player's UUID
     * @return true if online and cached as opted out
     */
    public boolean isOptedOut(UUID playerUUID) {
        if (playerUUID == null) {
            logger.warning("Attempted to check opt-out status with null UUID");
            return false;
        }
        return optedOutCache.contains(playerUUID);
    }

    /**
     * Sets a player's opt-out status: updates the cache synchronously, then
     * persists asynchronously so gameplay is never blocked on DB I/O.
     *
     * <p>Thread-safe. Concurrent calls for the same player converge to a
     * consistent cache state; DB write order is not guaranteed (last write
     * wins).</p>
     *
     * @param playerUUID Player's UUID
     * @param optedOut Whether the player is opted out
     */
    public void setOptOut(UUID playerUUID, boolean optedOut) {
        if (playerUUID == null) {
            logger.warning("Attempted to set opt-out status with null UUID");
            return;
        }

        if (optedOut) {
            optedOutCache.add(playerUUID);
        } else {
            optedOutCache.remove(playerUUID);
        }

        scheduler.runAsync(() -> {
            try {
                databaseManager.setOptOut(playerUUID, optedOut);
                logger.fine("Async DB write completed for player " + playerUUID + ": opted out = " + optedOut);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to write opt-out status to database for " + playerUUID, e);
                // Cache already updated, so gameplay is unaffected; DB resyncs on next write.
            }
        });
    }

    /**
     * Toggles a player's opt-out status.
     *
     * @param playerUUID Player's UUID
     * @return The new opt-out status
     */
    public boolean toggleOptOut(UUID playerUUID) {
        if (playerUUID == null) {
            logger.warning("Attempted to toggle opt-out status with null UUID");
            return false;
        }

        final boolean currentStatus = isOptedOut(playerUUID);
        final boolean newStatus = !currentStatus;
        setOptOut(playerUUID, newStatus);
        return newStatus;
    }

    /**
     * Loads the player's opt-out status from the database asynchronously on join.
     * Until the query completes the player is treated as not opted out (safe default).
     *
     * @param event Player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();
        final String playerName = player.getName();

        scheduler.runAsync(() -> {
            try {
                final boolean optedOut = databaseManager.isOptedOut(playerUUID);

                if (optedOut) {
                    optedOutCache.add(playerUUID);
                    logger.fine("Player " + playerName + " joined (opted out, added to cache)");

                    // Notify on the global thread (Folia-safe); isOnline guards against
                    // the async query finishing after the player disconnects.
                    scheduler.runGlobal(() -> {
                        if (player.isOnline()) {
                            player.sendMessage(ChatColor.GRAY
                                + "Reminder: you are opted out of StormtrooperX mob accuracy nerfs. Use "
                                + ChatColor.YELLOW + "/stormtrooperx optin"
                                + ChatColor.GRAY + " to opt back in.");
                        }
                    });
                } else {
                    logger.fine("Player " + playerName + " joined (not opted out)");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load opt-out status for " + playerName, e);
                // Player will be treated as not opted out (safe default)
            }
        });
    }

    /**
     * Evicts the player from the cache on quit to free memory.
     *
     * @param event Player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID playerUUID = event.getPlayer().getUniqueId();

        final boolean wasInCache = optedOutCache.remove(playerUUID);

        if (wasInCache) {
            logger.fine("Player " + event.getPlayer().getName() + " quit (removed from cache)");
        }
    }

    /**
     * Gets the current cache size (for debugging/monitoring).
     *
     * @return Number of players currently cached as opted out
     */
    public int getCacheSize() {
        return optedOutCache.size();
    }
}
