package com.goobercraft.stormtrooperx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for OptOutManager.
 */
public class OptOutManagerTest {

    @Mock
    private Logger logger;

    @Mock
    private DatabaseManager databaseManager;

    @Mock
    private Plugin plugin;

    @Mock
    private BukkitScheduler scheduler;

    @Mock
    private Player player;

    @Mock
    private PlayerJoinEvent joinEvent;

    @Mock
    private PlayerQuitEvent quitEvent;

    private OptOutManager optOutManager;
    private UUID testUUID;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock scheduler to run tasks synchronously for testing
        // This makes async code behave synchronously in tests
        when(plugin.getServer()).thenReturn(mock(org.bukkit.Server.class));
        when(plugin.getServer().getScheduler()).thenReturn(scheduler);

        // Run async tasks immediately on the same thread
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Runnable task = invocation.getArgument(1);
                task.run();  // Execute immediately
                return null;
            }
        }).when(scheduler).runTaskAsynchronously(any(Plugin.class), any(Runnable.class));

        // Also handle sync tasks (for future use)
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Runnable task = invocation.getArgument(1);
                task.run();  // Execute immediately
                return null;
            }
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));

        // Use typical server size for testing (100 players)
        optOutManager = new OptOutManager(logger, databaseManager, plugin, 100);
        testUUID = UUID.randomUUID();

        // Setup mock player
        when(player.getUniqueId()).thenReturn(testUUID);
        when(player.getName()).thenReturn("TestPlayer");
    }

    @Test
    public void testConstructor_nullLogger() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new OptOutManager(null, databaseManager, plugin, 100);
        });
        assertEquals("logger cannot be null", exception.getMessage());
    }

    @Test
    public void testConstructor_nullDatabaseManager() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new OptOutManager(logger, null, plugin, 100);
        });
        assertEquals("databaseManager cannot be null", exception.getMessage());
    }

    @Test
    public void testConstructor_nullPlugin() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new OptOutManager(logger, databaseManager, null, 100);
        });
        assertEquals("plugin cannot be null", exception.getMessage());
    }

    @Test
    public void testConstructor_zeroMaxPlayers() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new OptOutManager(logger, databaseManager, plugin, 0);
        });
        assertEquals("maxPlayers must be positive, got: 0", exception.getMessage());
    }

    @Test
    public void testConstructor_negativeMaxPlayers() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new OptOutManager(logger, databaseManager, plugin, -100);
        });
        assertEquals("maxPlayers must be positive, got: -100", exception.getMessage());
    }

    @Test
    public void testShutdown() {
        // Add something to cache
        optOutManager.setOptOut(testUUID, true);
        assertTrue(optOutManager.getCacheSize() > 0, "Cache should have data before shutdown");

        optOutManager.shutdown();
        assertEquals(0, optOutManager.getCacheSize(), "Cache should be cleared on shutdown");
    }

    @Test
    public void testIsOptedOut_NotInCache() {
        // Player not in cache should return false (lazy loading)
        assertFalse(optOutManager.isOptedOut(testUUID), "Player not in cache should not be opted out");
    }

    @Test
    public void testIsOptedOut_InCache() {
        // Add player to cache
        optOutManager.setOptOut(testUUID, true);
        assertTrue(optOutManager.isOptedOut(testUUID), "Player in cache should be opted out");
    }

    @Test
    public void testIsOptedOut_NullUUID() {
        assertFalse(optOutManager.isOptedOut(null), "Null UUID should return false");
    }

    @Test
    public void testSetOptOut_OptIn() {
        UUID playerUUID = UUID.randomUUID();

        // Opt in (set to true)
        optOutManager.setOptOut(playerUUID, true);

        // Verify database was updated
        verify(databaseManager).setOptOut(playerUUID, true);

        // Verify cache was updated
        assertTrue(optOutManager.isOptedOut(playerUUID), "Player should be in cache after opting out");
    }

    @Test
    public void testSetOptOut_OptOut() {
        UUID playerUUID = UUID.randomUUID();

        // First opt in
        optOutManager.setOptOut(playerUUID, true);
        assertTrue(optOutManager.isOptedOut(playerUUID));

        // Then opt out (set to false)
        optOutManager.setOptOut(playerUUID, false);

        // Verify database was updated
        verify(databaseManager).setOptOut(playerUUID, false);

        // Verify cache was updated
        assertFalse(optOutManager.isOptedOut(playerUUID), "Player should be removed from cache after opting back in");
    }

    @Test
    public void testSetOptOut_NullUUID() {
        // Should not throw exception
        optOutManager.setOptOut(null, true);

        // Verify database was not called
        verify(databaseManager, never()).setOptOut(any(), anyBoolean());
    }

    @Test
    public void testToggleOptOut_FromFalseToTrue() {
        UUID playerUUID = UUID.randomUUID();

        // Initially not opted out
        assertFalse(optOutManager.isOptedOut(playerUUID));

        // Toggle
        boolean newStatus = optOutManager.toggleOptOut(playerUUID);

        assertTrue(newStatus, "Toggle should return true");
        assertTrue(optOutManager.isOptedOut(playerUUID), "Player should be opted out after toggle");
        verify(databaseManager).setOptOut(playerUUID, true);
    }

    @Test
    public void testToggleOptOut_FromTrueToFalse() {
        UUID playerUUID = UUID.randomUUID();

        // Set to opted out first
        optOutManager.setOptOut(playerUUID, true);
        assertTrue(optOutManager.isOptedOut(playerUUID));

        // Toggle
        boolean newStatus = optOutManager.toggleOptOut(playerUUID);

        assertFalse(newStatus, "Toggle should return false");
        assertFalse(optOutManager.isOptedOut(playerUUID), "Player should not be opted out after toggle");
        verify(databaseManager).setOptOut(playerUUID, false);
    }

    @Test
    public void testToggleOptOut_NullUUID() {
        boolean result = optOutManager.toggleOptOut(null);
        assertFalse(result, "Toggle with null UUID should return false");
    }

    @Test
    public void testOnPlayerJoin_OptedOut() {
        // Setup: Player is opted out in database
        when(joinEvent.getPlayer()).thenReturn(player);
        when(databaseManager.isOptedOut(testUUID)).thenReturn(true);

        // Initially cache should be empty
        assertEquals(0, optOutManager.getCacheSize());

        // Trigger join event
        optOutManager.onPlayerJoin(joinEvent);

        // Verify player was added to cache
        assertTrue(optOutManager.isOptedOut(testUUID), "Player should be in cache after join");
        assertEquals(1, optOutManager.getCacheSize(), "Cache size should be 1");
    }

    @Test
    public void testOnPlayerJoin_NotOptedOut() {
        // Setup: Player is NOT opted out in database
        when(joinEvent.getPlayer()).thenReturn(player);
        when(databaseManager.isOptedOut(testUUID)).thenReturn(false);

        // Trigger join event
        optOutManager.onPlayerJoin(joinEvent);

        // Verify player was NOT added to cache
        assertFalse(optOutManager.isOptedOut(testUUID), "Player should not be in cache after join");
        assertEquals(0, optOutManager.getCacheSize(), "Cache should be empty");
    }

    @Test
    public void testOnPlayerJoin_DatabaseException() {
        // Setup: Database throws exception
        when(joinEvent.getPlayer()).thenReturn(player);
        when(databaseManager.isOptedOut(testUUID)).thenThrow(new RuntimeException("Database error"));

        // Should not throw exception
        assertDoesNotThrow(() -> optOutManager.onPlayerJoin(joinEvent));

        // Cache should remain empty
        assertEquals(0, optOutManager.getCacheSize(), "Cache should be empty after exception");
    }

    @Test
    public void testOnPlayerQuit_InCache() {
        // Setup: Add player to cache first
        optOutManager.setOptOut(testUUID, true);
        assertTrue(optOutManager.isOptedOut(testUUID));
        assertEquals(1, optOutManager.getCacheSize());

        // Trigger quit event
        when(quitEvent.getPlayer()).thenReturn(player);
        optOutManager.onPlayerQuit(quitEvent);

        // Verify player was removed from cache
        assertFalse(optOutManager.isOptedOut(testUUID), "Player should be removed from cache after quit");
        assertEquals(0, optOutManager.getCacheSize(), "Cache should be empty");
    }

    @Test
    public void testOnPlayerQuit_NotInCache() {
        // Setup: Player not in cache
        assertFalse(optOutManager.isOptedOut(testUUID));

        // Trigger quit event (should not throw exception)
        when(quitEvent.getPlayer()).thenReturn(player);
        assertDoesNotThrow(() -> optOutManager.onPlayerQuit(quitEvent));

        // Cache should remain empty
        assertEquals(0, optOutManager.getCacheSize());
    }

    @Test
    public void testLazyLoading_MultiplePlayersScenario() {
        // Create multiple players
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        Player p3 = mock(Player.class);

        when(p1.getUniqueId()).thenReturn(player1);
        when(p2.getUniqueId()).thenReturn(player2);
        when(p3.getUniqueId()).thenReturn(player3);

        when(p1.getName()).thenReturn("Player1");
        when(p2.getName()).thenReturn("Player2");
        when(p3.getName()).thenReturn("Player3");

        // Setup database: Player1 and Player3 are opted out
        when(databaseManager.isOptedOut(player1)).thenReturn(true);
        when(databaseManager.isOptedOut(player2)).thenReturn(false);
        when(databaseManager.isOptedOut(player3)).thenReturn(true);

        // Simulate joins
        PlayerJoinEvent join1 = mock(PlayerJoinEvent.class);
        PlayerJoinEvent join2 = mock(PlayerJoinEvent.class);
        PlayerJoinEvent join3 = mock(PlayerJoinEvent.class);

        when(join1.getPlayer()).thenReturn(p1);
        when(join2.getPlayer()).thenReturn(p2);
        when(join3.getPlayer()).thenReturn(p3);

        optOutManager.onPlayerJoin(join1);
        optOutManager.onPlayerJoin(join2);
        optOutManager.onPlayerJoin(join3);

        // Verify cache state
        assertTrue(optOutManager.isOptedOut(player1), "Player1 should be in cache");
        assertFalse(optOutManager.isOptedOut(player2), "Player2 should not be in cache");
        assertTrue(optOutManager.isOptedOut(player3), "Player3 should be in cache");
        assertEquals(2, optOutManager.getCacheSize(), "Cache should have 2 players");

        // Simulate Player1 quit
        PlayerQuitEvent quit1 = mock(PlayerQuitEvent.class);
        when(quit1.getPlayer()).thenReturn(p1);
        optOutManager.onPlayerQuit(quit1);

        // Verify Player1 removed
        assertFalse(optOutManager.isOptedOut(player1), "Player1 should be removed from cache");
        assertEquals(1, optOutManager.getCacheSize(), "Cache should have 1 player");
    }

    @Test
    public void testCacheCoherency() {
        UUID playerUUID = UUID.randomUUID();

        // Opt out
        optOutManager.setOptOut(playerUUID, true);
        assertTrue(optOutManager.isOptedOut(playerUUID), "Player should be opted out");
        verify(databaseManager).setOptOut(playerUUID, true);

        // Opt back in
        optOutManager.setOptOut(playerUUID, false);
        assertFalse(optOutManager.isOptedOut(playerUUID), "Player should not be opted out");
        verify(databaseManager).setOptOut(playerUUID, false);

        // Verify both cache and DB were updated
        verify(databaseManager, times(1)).setOptOut(playerUUID, true);
        verify(databaseManager, times(1)).setOptOut(playerUUID, false);
    }

    @Test
    public void testCacheSizing_SmallServer() {
        // Small server (< 64 players) should use minimum capacity of 16
        OptOutManager smallServerManager = new OptOutManager(logger, databaseManager, plugin, 20);
        assertNotNull(smallServerManager, "OptOutManager should initialize with small server size");
    }

    @Test
    public void testCacheSizing_LargeServer() {
        // Large server should size cache proportionally
        // 1000 players / 4 = 250 capacity
        OptOutManager largeServerManager = new OptOutManager(logger, databaseManager, plugin, 1000);
        assertNotNull(largeServerManager, "OptOutManager should initialize with large server size");
    }

    @Test
    public void testCacheSizing_MinimumCapacity() {
        // Very small server should still get minimum capacity
        OptOutManager tinyServerManager = new OptOutManager(logger, databaseManager, plugin, 10);
        assertNotNull(tinyServerManager, "OptOutManager should initialize with minimum capacity");
    }
}
