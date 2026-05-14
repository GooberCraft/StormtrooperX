package com.goobercraft.stormtrooperx;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StormtrooperXExpansion — PlaceholderAPI bridge")
class StormtrooperXExpansionTest {

    private StormtrooperX plugin;
    private OptOutManager optOutManager;
    private PluginDescriptionFile description;
    private StormtrooperXExpansion expansion;

    @BeforeEach
    void setUp() {
        plugin = mock(StormtrooperX.class);
        optOutManager = mock(OptOutManager.class);
        description = mock(PluginDescriptionFile.class);
        lenient().doReturn(description).when(plugin).getDescription();
        lenient().when(description.getAuthors()).thenReturn(List.of("GooberCraft"));
        lenient().when(description.getVersion()).thenReturn("9.9.9");

        expansion = new StormtrooperXExpansion(plugin, optOutManager);
    }

    @Test
    void testConstructor_nullPlugin_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new StormtrooperXExpansion(null, optOutManager));
    }

    @Test
    void testConstructor_nullOptOutManager_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new StormtrooperXExpansion(plugin, null));
    }

    @Test
    void testGetIdentifier() {
        assertEquals("stormtrooperx", expansion.getIdentifier());
    }

    @Test
    void testGetAuthor_joinsAuthorsList() {
        when(description.getAuthors()).thenReturn(List.of("GooberCraft", "byteful"));
        assertEquals("GooberCraft, byteful", expansion.getAuthor());
    }

    @Test
    void testGetVersion_returnsPluginVersion() {
        assertEquals("9.9.9", expansion.getVersion());
    }

    @Test
    void testPersist_returnsTrue() {
        assertTrue(expansion.persist());
    }

    @Test
    void testOnRequest_nullPlayer_returnsEmptyString() {
        assertEquals("", expansion.onRequest(null, "optout"));
    }

    @Test
    void testOnRequest_optoutPlaceholder_optedOut_returnsTrue() {
        OfflinePlayer player = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(optOutManager.isOptedOut(uuid)).thenReturn(true);

        assertEquals("true", expansion.onRequest(player, "optout"));
    }

    @Test
    void testOnRequest_optoutPlaceholder_notOptedOut_returnsFalse() {
        OfflinePlayer player = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(optOutManager.isOptedOut(uuid)).thenReturn(false);

        assertEquals("false", expansion.onRequest(player, "optout"));
    }

    @Test
    void testOnRequest_caseInsensitive() {
        OfflinePlayer player = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(optOutManager.isOptedOut(uuid)).thenReturn(true);

        assertEquals("true", expansion.onRequest(player, "OptOut"));
        assertEquals("true", expansion.onRequest(player, "OPTOUT"));
    }

    @Test
    void testOnRequest_unknownPlaceholder_returnsNull() {
        OfflinePlayer player = mock(OfflinePlayer.class);
        assertNull(expansion.onRequest(player, "nonexistent"));
    }
}