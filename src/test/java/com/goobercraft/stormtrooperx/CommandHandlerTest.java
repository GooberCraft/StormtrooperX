package com.goobercraft.stormtrooperx;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandHandlerTest {

    private StormtrooperX plugin;

    @Mock
    private OptOutManager optOutManager;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(StormtrooperX.class, CALLS_REAL_METHODS);

        Field entityConfigsField = StormtrooperX.class.getDeclaredField("entityConfigs");
        entityConfigsField.setAccessible(true);
        entityConfigsField.set(plugin, new EnumMap<>(EntityType.class));

        Field optOutManagerField = StormtrooperX.class.getDeclaredField("optOutManager");
        optOutManagerField.setAccessible(true);
        optOutManagerField.set(plugin, optOutManager);

        Field debugField = StormtrooperX.class.getDeclaredField("debug");
        debugField.setAccessible(true);
        debugField.set(plugin, false);
    }

    // -------------------------------------------------------------------------
    // Wrong command name
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_wrongCommandName_returnsFalse() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("minecraft");

        boolean result = plugin.onCommand(sender, command, "minecraft", new String[]{});

        assertFalse(result);
        verify(sender, never()).sendMessage(anyString());
    }

    // -------------------------------------------------------------------------
    // No-args: status display
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_noArgs_displaysStatusAndReturnsTrue() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(command.getName()).thenReturn("stormtrooperx");
        doReturn(description).when(plugin).getDescription();
        when(description.getVersion()).thenReturn("1.7.0");

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.GOLD + "  StormtrooperX v1.7.0");
    }

    // -------------------------------------------------------------------------
    // reload subcommand
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_reload_noPermission_sendsErrorAndReturnsTrue() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.reload")).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"reload"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
    }

    @Test
    void testOnCommand_reload_hasPermission_reloadsAndConfirms() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.reload")).thenReturn(true);
        doNothing().when(plugin).reloadConfig();
        doReturn(mockConfig).when(plugin).getConfig();
        when(mockConfig.getInt("config-version", 2)).thenReturn(3);
        when(mockConfig.getBoolean("debug", false)).thenReturn(false);
        when(mockConfig.contains(anyString())).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"reload"});

        assertTrue(result);
        verify(plugin).reloadConfig();
        verify(sender).sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
    }

    @Test
    void testOnCommand_reload_caseInsensitive() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.reload")).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"RELOAD"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
    }

    // -------------------------------------------------------------------------
    // optout subcommand
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_optout_notPlayer_sendsErrorAndReturnsTrue() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.RED + "Only players can use this command!");
    }

    @Test
    void testOnCommand_optout_noPermission_sendsErrorAndReturnsTrue() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.RED + "You don't have permission to opt-out!");
    }

    @Test
    void testOnCommand_optout_optsOut_sendsOptedOutConfirmation() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.toggleOptOut(uuid)).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.GREEN + "You have opted out of mob accuracy nerfs!");
        verify(sender).sendMessage(ChatColor.YELLOW + "Mobs will shoot at you with normal accuracy.");
    }

    @Test
    void testOnCommand_optout_optsIn_sendsOptedInConfirmation() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.toggleOptOut(uuid)).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.GREEN + "You have opted back in to mob accuracy nerfs!");
        verify(sender).sendMessage(ChatColor.YELLOW + "Mobs will now have reduced accuracy when shooting at you.");
    }

    @Test
    void testOnCommand_toggleAlias_behavesIdenticallyToOptout() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.toggleOptOut(uuid)).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"toggle"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.GREEN + "You have opted out of mob accuracy nerfs!");
    }

    // -------------------------------------------------------------------------
    // Unknown subcommand
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_unknownSubcommand_sendsErrorAndReturnsTrue() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"foobar"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.RED + "Unknown command. Use /stormtrooperx for help.");
    }
}
