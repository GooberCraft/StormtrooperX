package com.goobercraft.stormtrooperx;

import org.bukkit.ChatColor;
import org.bukkit.Server;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);

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
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
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
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);

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
        verify(sender).sendMessage(ChatColor.RED + "Only players can use this command without a target!");
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
    void testOnCommand_optout_whenNotOptedOut_setsAndConfirms() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.isOptedOut(uuid)).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout"});

        assertTrue(result);
        verify(optOutManager).setOptOut(uuid, true);
        verify(optOutManager, never()).toggleOptOut(uuid);
        verify(sender).sendMessage(ChatColor.GREEN + "You have opted out of mob accuracy nerfs!");
        verify(sender).sendMessage(ChatColor.YELLOW + "Mobs will shoot at you with normal accuracy.");
    }

    @Test
    void testOnCommand_optout_whenAlreadyOptedOut_idempotentMessage() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.isOptedOut(uuid)).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout"});

        assertTrue(result);
        verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
        verify(sender).sendMessage(ChatColor.YELLOW + "You are already opted out of mob accuracy nerfs.");
    }

    @Test
    void testOnCommand_optin_whenOptedOut_setsAndConfirms() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.isOptedOut(uuid)).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optin"});

        assertTrue(result);
        verify(optOutManager).setOptOut(uuid, false);
        verify(sender).sendMessage(ChatColor.GREEN + "You have opted back in to mob accuracy nerfs!");
        verify(sender).sendMessage(ChatColor.YELLOW + "Mobs will now have reduced accuracy when shooting at you.");
    }

    @Test
    void testOnCommand_optin_whenAlreadyOptedIn_idempotentMessage() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.isOptedOut(uuid)).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optin"});

        assertTrue(result);
        verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
        verify(sender).sendMessage(ChatColor.YELLOW + "You are already opted in to mob accuracy nerfs.");
    }

    @Test
    void testOnCommand_optin_notPlayer_sendsError() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optin"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.RED + "Only players can use this command without a target!");
    }

    // -------------------------------------------------------------------------
    // toggle subcommand (flips state)
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_toggle_flipsToOptedOut() {
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

    @Test
    void testOnCommand_toggle_flipsToOptedIn() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(optOutManager.toggleOptOut(uuid)).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"toggle"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.GREEN + "You have opted back in to mob accuracy nerfs!");
    }

    @Test
    void testOnCommand_toggle_notPlayer_sendsError() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"toggle"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.RED + "Only players can use this command!");
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Test
    void testOnTabComplete_wrongCommandName_returnsEmpty() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("minecraft");

        List<String> result = plugin.onTabComplete(sender, command, "minecraft", new String[]{""});

        assertTrue(result.isEmpty());
    }

    @Test
    void testOnTabComplete_consoleWithReloadPerm_suggestsHelpAndReload() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{""});

        assertEquals(List.of("help", "reload"), result);
    }

    @Test
    void testOnTabComplete_playerWithBothPerms_suggestsAllSorted() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{""});

        // Alphabetical
        assertEquals(List.of("help", "optin", "optout", "reload", "toggle"), result);
    }

    @Test
    void testOnTabComplete_playerWithOptoutOnly_suggestsHelpOptinOptoutToggle() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{""});

        assertEquals(List.of("help", "optin", "optout", "toggle"), result);
    }

    @Test
    void testOnTabComplete_consoleWithoutOptoutPerm_onlyHelp() {
        // Console with no perms still gets `help` (no permission required for help)
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{""});

        assertEquals(List.of("help"), result);
    }

    @Test
    void testOnTabComplete_consoleWithOptoutOthersPerm_suggestsOptoutOptinForAdminTargets() {
        // Console can't self-optout (not a player) but with optout.others can admin-target
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");
        lenient().when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{""});

        assertEquals(List.of("help", "optin", "optout"), result);
    }

    @Test
    void testOnTabComplete_partialPrefix_filtersToMatches() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{"re"});

        assertEquals(List.of("reload"), result);
    }

    @Test
    void testOnTabComplete_partialPrefixCaseInsensitive() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{"OP"});

        // "OP" matches both optin and optout
        assertEquals(List.of("optin", "optout"), result);
    }

    @Test
    void testOnTabComplete_secondArgOfReload_returnsEmpty() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{"reload", ""});

        assertTrue(result.isEmpty());
    }

    @Test
    void testOnTabComplete_secondArgOfOptout_withoutAdminPerm_returnsEmpty() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(false);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{"optout", ""});

        assertTrue(result.isEmpty());
    }

    @Test
    void testOnTabComplete_noMatchingPrefix_returnsEmpty() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{"xyz"});

        assertTrue(result.isEmpty());
    }

    @Test
    void testOnTabComplete_secondArgOfOptout_withAdminPerm_suggestsOnlinePlayers() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        Server server = mock(Server.class);
        Player alice = mock(Player.class);
        Player bob = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
        doReturn(server).when(plugin).getServer();
        LinkedHashSet<Player> online = new LinkedHashSet<>();
        online.add(alice);
        online.add(bob);
        doReturn(online).when(server).getOnlinePlayers();
        when(alice.getName()).thenReturn("Alice");
        when(bob.getName()).thenReturn("Bob");

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{"optout", ""});

        assertEquals(List.of("Alice", "Bob"), result);
    }

    @Test
    void testOnTabComplete_secondArgOfOptin_withAdminPerm_filtersByPrefix() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        Server server = mock(Server.class);
        Player alice = mock(Player.class);
        Player bob = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
        doReturn(server).when(plugin).getServer();
        LinkedHashSet<Player> online = new LinkedHashSet<>();
        online.add(alice);
        online.add(bob);
        doReturn(online).when(server).getOnlinePlayers();
        when(alice.getName()).thenReturn("Alice");
        when(bob.getName()).thenReturn("Bob");

        List<String> result = plugin.onTabComplete(sender, command, "stx", new String[]{"optin", "B"});

        assertEquals(List.of("Bob"), result);
    }

    // -------------------------------------------------------------------------
    // help subcommand
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_help_playerWithOptout_listsSelfCommands() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        lenient().when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"help"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.GOLD + "  StormtrooperX Commands");
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout" + ChatColor.GRAY + " - Opt yourself out of mob accuracy nerfs");
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optin" + ChatColor.GRAY + " - Opt yourself back in");
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx toggle" + ChatColor.GRAY + " - Flip your opt-out state");
        verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " - Reload configuration");
        verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout <player>" + ChatColor.GRAY + " - Force a player to opt out");
    }

    @Test
    void testOnCommand_help_admin_listsAllCommands() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
        when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"help"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " - Reload configuration");
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout <player>" + ChatColor.GRAY + " - Force a player to opt out");
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optin <player>" + ChatColor.GRAY + " - Force a player to opt in");
    }

    @Test
    void testOnCommand_help_consoleWithoutPerms_onlyBasicCommands() {
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(command.getName()).thenReturn("stormtrooperx");

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"help"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx" + ChatColor.GRAY + " - Show plugin status");
        verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx help" + ChatColor.GRAY + " - Show this help");
        verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout" + ChatColor.GRAY + " - Opt yourself out of mob accuracy nerfs");
        verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " - Reload configuration");
    }

    // -------------------------------------------------------------------------
    // Admin opt-out/opt-in (target other players)
    // -------------------------------------------------------------------------

    @Test
    void testOnCommand_adminOptout_targetOnline_setsAndNotifiesBoth() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        Server server = mock(Server.class);
        Player target = mock(Player.class);
        UUID targetUuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
        doReturn(server).when(plugin).getServer();
        when(server.getPlayer("Bob")).thenReturn(target);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("Bob");
        when(optOutManager.isOptedOut(targetUuid)).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout", "Bob"});

        assertTrue(result);
        verify(optOutManager).setOptOut(targetUuid, true);
        verify(sender).sendMessage(ChatColor.GREEN + "Bob has been opted out of mob accuracy nerfs.");
        verify(target).sendMessage(ChatColor.YELLOW + "An admin has opted you out of mob accuracy nerfs.");
    }

    @Test
    void testOnCommand_adminOptout_targetOffline_sendsError() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        Server server = mock(Server.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
        doReturn(server).when(plugin).getServer();
        when(server.getPlayer("Ghost")).thenReturn(null);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout", "Ghost"});

        assertTrue(result);
        verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
        verify(sender).sendMessage(ChatColor.RED + "Player 'Ghost' is not online.");
    }

    @Test
    void testOnCommand_adminOptout_noPermission_sendsError() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout", "Bob"});

        assertTrue(result);
        verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
        verify(sender).sendMessage(ChatColor.RED + "You don't have permission to manage other players' opt-out status!");
    }

    @Test
    void testOnCommand_adminOptout_targetAlreadyOptedOut_idempotent() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        Server server = mock(Server.class);
        Player target = mock(Player.class);
        UUID targetUuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
        doReturn(server).when(plugin).getServer();
        when(server.getPlayer("Bob")).thenReturn(target);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("Bob");
        when(optOutManager.isOptedOut(targetUuid)).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout", "Bob"});

        assertTrue(result);
        verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
        verify(sender).sendMessage(ChatColor.YELLOW + "Bob is already opted out.");
        verify(target, never()).sendMessage(anyString());
    }

    @Test
    void testOnCommand_adminOptin_targetOptedOut_setsAndNotifies() {
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        Server server = mock(Server.class);
        Player target = mock(Player.class);
        UUID targetUuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
        doReturn(server).when(plugin).getServer();
        when(server.getPlayer("Bob")).thenReturn(target);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("Bob");
        when(optOutManager.isOptedOut(targetUuid)).thenReturn(true);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optin", "Bob"});

        assertTrue(result);
        verify(optOutManager).setOptOut(targetUuid, false);
        verify(sender).sendMessage(ChatColor.GREEN + "Bob has been opted back in to mob accuracy nerfs.");
        verify(target).sendMessage(ChatColor.YELLOW + "An admin has opted you in to mob accuracy nerfs.");
    }

    @Test
    void testOnCommand_adminOptout_targetIsSender_skipsTargetMessage() {
        // Admin targeting themselves shouldn't get the "an admin has..." duplicate message
        Command command = mock(Command.class);
        Player sender = mock(Player.class);
        Server server = mock(Server.class);
        UUID uuid = UUID.randomUUID();
        when(command.getName()).thenReturn("stormtrooperx");
        when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
        doReturn(server).when(plugin).getServer();
        when(server.getPlayer("self")).thenReturn(sender);
        when(sender.getUniqueId()).thenReturn(uuid);
        when(sender.getName()).thenReturn("self");
        when(optOutManager.isOptedOut(uuid)).thenReturn(false);

        boolean result = plugin.onCommand(sender, command, "stx", new String[]{"optout", "self"});

        assertTrue(result);
        verify(sender).sendMessage(ChatColor.GREEN + "self has been opted out of mob accuracy nerfs.");
        verify(sender, never()).sendMessage(ChatColor.YELLOW + "An admin has opted you out of mob accuracy nerfs.");
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
        verify(sender).sendMessage(ChatColor.RED + "Unknown command. Use /stormtrooperx help for a list of commands.");
    }
}
