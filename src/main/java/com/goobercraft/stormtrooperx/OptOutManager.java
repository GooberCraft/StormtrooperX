package com.goobercraft.stormtrooperx;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Manages player opt-out preferences with an in-memory cache.
 * Uses lazy loading strategy: loads opt-out status on player join, removes on quit.
 * Database operations are performed asynchronously to avoid blocking the main thread.
 *
 * This class acts as a facade coordinating between the cache layer and DatabaseManager.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The internal cache uses a
 * {@link ConcurrentHashMap}-backed {@link Set}, which provides thread-safe operations
 * without explicit synchronization. All database operations are performed asynchronously
 * on separate threads via Bukkit's scheduler, ensuring the main game thread is never blocked.
 * Cache reads (via {@link #isOptedOut(UUID)}) are lock-free and safe to call from any thread.
 * Cache writes are atomic and coordination between threads is handled by the underlying
 * {@link ConcurrentHashMap}.</p>
 */
public class OptOutManager implements Listener {

    private final Logger logger;
    private final DatabaseManager databaseManager;
    private final Plugin plugin;
    private final Set<UUID> optedOutCache;

    /**
     * Creates a new opt-out manager.
     *
     * @param logger Logger instance (must not be null)
     * @param databaseManager Database manager for persistence (must not be null)
     * @param plugin Plugin instance for async task scheduling (must not be null)
     * @param maxPlayers Maximum number of players on the server (must be positive)
     * @throws IllegalArgumentException if any parameter is null or maxPlayers is not positive
     */
    public OptOutManager(Logger logger, DatabaseManager databaseManager, Plugin plugin, int maxPlayers) {
        // Defensive: validate constructor parameters
        if (logger == null) {
            throw new IllegalArgumentException("logger cannot be null");
        }
        if (databaseManager == null) {
            throw new IllegalArgumentException("databaseManager cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("maxPlayers must be positive, got: " + maxPlayers);
        }

        this.logger = logger;
        this.databaseManager = databaseManager;
        this.plugin = plugin;

        // Size cache for ~25% of max players opting out (conservative estimate)
        // Use thread-safe ConcurrentHashMap-backed set for async operations
        // Cap at 16384 to prevent excessive memory allocation on misconfigured servers
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
     * Uses in-memory cache for fast O(1) lookups.
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and lock-free.
     * It can be safely called from the main game thread during event handling
     * or from async threads. The underlying {@link ConcurrentHashMap} provides
     * thread-safe read operations without blocking.</p>
     *
     * @param playerUUID Player's UUID
     * @return true if opted out, false otherwise
     */
    public boolean isOptedOut(UUID playerUUID) {
        if (playerUUID == null) {
            logger.warning("Attempted to check opt-out status with null UUID");
            return false;
        }

        // Check cache (only online players are cached)
        return optedOutCache.contains(playerUUID);
    }

    /**
     * Sets a player's opt-out status.
     * Updates cache immediately (synchronous) and database asynchronously.
     * This ensures gameplay is not blocked by database I/O.
     *
     * <p><b>Thread Safety:</b> This method is thread-safe. The cache update
     * is atomic (provided by {@link ConcurrentHashMap}), and the database write
     * is delegated to an async thread. If called from multiple threads simultaneously
     * for the same player, the final state will be consistent, though the order of
     * database writes is not guaranteed (last write wins).</p>
     *
     * @param playerUUID Player's UUID
     * @param optedOut Whether the player is opted out
     */
    public void setOptOut(UUID playerUUID, boolean optedOut) {
        if (playerUUID == null) {
            logger.warning("Attempted to set opt-out status with null UUID");
            return;
        }

        // Update cache immediately (thread-safe, non-blocking)
        if (optedOut) {
            optedOutCache.add(playerUUID);
        } else {
            optedOutCache.remove(playerUUID);
        }

        // Write to database asynchronously to avoid blocking main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                databaseManager.setOptOut(playerUUID, optedOut);
                logger.fine("Async DB write completed for player " + playerUUID + ": opted out = " + optedOut);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to write opt-out status to database for " + playerUUID, e);
                // Note: Cache is already updated, so gameplay continues normally
                // The database will be out of sync until next successful write
            }
        });
    }

    /**
     * Toggles a player's opt-out status.
     * Updates both the database and in-memory cache.
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
     * Handles player join events.
     * Loads the player's opt-out status from database asynchronously to avoid blocking main thread.
     * Until the async query completes, the player is assumed to not be opted out (safe default).
     *
     * @param event Player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final UUID playerUUID = event.getPlayer().getUniqueId();
        final String playerName = event.getPlayer().getName();

        // Query database asynchronously to avoid blocking main thread during player join
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Query database for opt-out status (off main thread)
                final boolean optedOut = databaseManager.isOptedOut(playerUUID);

                if (optedOut) {
                    // Add to cache (thread-safe ConcurrentHashMap)
                    optedOutCache.add(playerUUID);
                    logger.fine("Player " + playerName + " joined (opted out, added to cache)");
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
     * Handles player quit events.
     * Removes the player from the cache to save memory.
     *
     * @param event Player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID playerUUID = event.getPlayer().getUniqueId();

        // Remove from cache (will be reloaded on next join)
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
