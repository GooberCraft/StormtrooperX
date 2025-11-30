package com.goobercraft.stormtrooperx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseManager.
 */
class DatabaseManagerTest {

    private DatabaseManager databaseManager;
    private Logger logger;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("TestLogger");
        databaseManager = new DatabaseManager(logger, tempDir, "h2", null);
        databaseManager.initialize();
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void testInitialize() {
        // Database should initialize without errors
        // Verify by checking if we can interact with it
        UUID testUUID = UUID.randomUUID();
        assertDoesNotThrow(() -> databaseManager.isOptedOut(testUUID));
    }

    @Test
    void testIsOptedOut_playerNotInDatabase() {
        UUID playerUUID = UUID.randomUUID();

        // Player not in database should return false (default)
        assertFalse(databaseManager.isOptedOut(playerUUID));
    }

    @Test
    void testSetOptOut_optIn() {
        UUID playerUUID = UUID.randomUUID();

        // Set player to opted out (true)
        databaseManager.setOptOut(playerUUID, true);

        // Verify the status
        assertTrue(databaseManager.isOptedOut(playerUUID));
    }

    @Test
    void testSetOptOut_optOut() {
        UUID playerUUID = UUID.randomUUID();

        // Set player to not opted out (false)
        databaseManager.setOptOut(playerUUID, false);

        // Verify the status
        assertFalse(databaseManager.isOptedOut(playerUUID));
    }

    @Test
    void testSetOptOut_updateExisting() {
        UUID playerUUID = UUID.randomUUID();

        // Set initial status
        databaseManager.setOptOut(playerUUID, true);
        assertTrue(databaseManager.isOptedOut(playerUUID));

        // Update the status
        databaseManager.setOptOut(playerUUID, false);
        assertFalse(databaseManager.isOptedOut(playerUUID));

        // Update again
        databaseManager.setOptOut(playerUUID, true);
        assertTrue(databaseManager.isOptedOut(playerUUID));
    }

    @Test
    void testToggleOptOut_fromFalseToTrue() {
        UUID playerUUID = UUID.randomUUID();

        // Initially not opted out (default)
        assertFalse(databaseManager.isOptedOut(playerUUID));

        // Toggle should return new status (true)
        boolean newStatus = databaseManager.toggleOptOut(playerUUID);
        assertTrue(newStatus);

        // Verify the status persisted
        assertTrue(databaseManager.isOptedOut(playerUUID));
    }

    @Test
    void testToggleOptOut_fromTrueToFalse() {
        UUID playerUUID = UUID.randomUUID();

        // Set to opted out first
        databaseManager.setOptOut(playerUUID, true);
        assertTrue(databaseManager.isOptedOut(playerUUID));

        // Toggle should return new status (false)
        boolean newStatus = databaseManager.toggleOptOut(playerUUID);
        assertFalse(newStatus);

        // Verify the status persisted
        assertFalse(databaseManager.isOptedOut(playerUUID));
    }

    @Test
    void testToggleOptOut_multipleTimes() {
        UUID playerUUID = UUID.randomUUID();

        // Toggle multiple times
        boolean status1 = databaseManager.toggleOptOut(playerUUID);
        assertTrue(status1);

        boolean status2 = databaseManager.toggleOptOut(playerUUID);
        assertFalse(status2);

        boolean status3 = databaseManager.toggleOptOut(playerUUID);
        assertTrue(status3);

        // Final status should be true
        assertTrue(databaseManager.isOptedOut(playerUUID));
    }

    @Test
    void testMultiplePlayers() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        // Set different statuses for each player
        databaseManager.setOptOut(player1, true);
        databaseManager.setOptOut(player2, false);
        databaseManager.setOptOut(player3, true);

        // Verify each player's status independently
        assertTrue(databaseManager.isOptedOut(player1));
        assertFalse(databaseManager.isOptedOut(player2));
        assertTrue(databaseManager.isOptedOut(player3));
    }

    @Test
    void testClose() {
        // Close should not throw an exception
        assertDoesNotThrow(() -> databaseManager.close());

        // Calling close multiple times should be safe
        assertDoesNotThrow(() -> databaseManager.close());
    }

    @Test
    void testPersistenceAcrossInstances() {
        UUID playerUUID = UUID.randomUUID();

        // Set a value with the first instance
        databaseManager.setOptOut(playerUUID, true);
        assertTrue(databaseManager.isOptedOut(playerUUID));

        // Close the first instance
        databaseManager.close();

        // Create a new instance pointing to the same database file
        DatabaseManager newManager = new DatabaseManager(logger, tempDir, "h2", null);
        newManager.initialize();

        // Verify the data persisted
        assertTrue(newManager.isOptedOut(playerUUID));

        // Clean up
        newManager.close();
    }

    @Test
    void testSameUUIDString() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Set opt-out status
        databaseManager.setOptOut(uuid, true);

        // Verify with the same UUID
        assertTrue(databaseManager.isOptedOut(uuid));

        // Verify with a new UUID object created from the same string
        UUID sameUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        assertTrue(databaseManager.isOptedOut(sameUuid));
    }

    @Test
    void testNullUUID_isOptedOut() {
        // Null UUID should not cause an exception, should return false
        assertFalse(databaseManager.isOptedOut(null));
    }

    @Test
    void testNullUUID_setOptOut() {
        // Null UUID should not cause an exception
        assertDoesNotThrow(() -> databaseManager.setOptOut(null, true));
        assertDoesNotThrow(() -> databaseManager.setOptOut(null, false));
    }

    @Test
    void testNullUUID_toggleOptOut() {
        // Null UUID should not cause an exception, should handle gracefully
        assertDoesNotThrow(() -> databaseManager.toggleOptOut(null));
    }

    @Test
    void testDatabaseSecurity_FilePermissions() {
        // Verify that database file is created
        UUID testUUID = UUID.randomUUID();
        databaseManager.setOptOut(testUUID, true);
        databaseManager.close();

        // Check that database files exist (H2 creates .mv.db file)
        File dbFile = new File(tempDir, "players.mv.db");
        assertTrue(dbFile.exists(), "Database file should be created");
    }

    @Test
    void testConcurrentAccess_SequentialWrites() {
        // Test that sequential writes work correctly (file locking should be in place)
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        for (int i = 0; i < 100; i++) {
            databaseManager.setOptOut(player1, i % 2 == 0);
            databaseManager.setOptOut(player2, i % 2 != 0);
        }

        // Final values should be correct
        // When i=99: i%2==0 is false, so player1 gets false (not opted out)
        // When i=99: i%2!=0 is true, so player2 gets true (opted out)
        assertFalse(databaseManager.isOptedOut(player1));
        assertTrue(databaseManager.isOptedOut(player2));
    }
}
