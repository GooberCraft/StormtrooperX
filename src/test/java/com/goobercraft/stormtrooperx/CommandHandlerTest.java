package com.goobercraft.stormtrooperx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.goobercraft.stormtrooperx.support.TestSupport;

/**
 * Tests for the {@code /stormtrooperx} command and its tab-completer.
 *
 * <p>Each subcommand has its own {@code @Nested} group so failures point
 * at the right surface in IDE and Surefire reports.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("/stormtrooperx command + tab completion")
class CommandHandlerTest {

    private StormtrooperX plugin;

    @Mock
    private OptOutManager optOutManager;

    @BeforeEach
    void setUp() {
        plugin = mock(StormtrooperX.class, CALLS_REAL_METHODS);
        TestSupport.inject(plugin, "entityConfigs", new EnumMap<>(EntityType.class));
        TestSupport.inject(plugin, "optOutManager", optOutManager);
        TestSupport.inject(plugin, "debug", false);
    }

    private Command stxCommand() {
        final Command command = mock(Command.class);
        when(command.getName()).thenReturn("stormtrooperx");
        return command;
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("returns false for a command whose name is not 'stormtrooperx'")
        void wrongCommandName() {
            final Command command = mock(Command.class);
            final CommandSender sender = mock(CommandSender.class);
            when(command.getName()).thenReturn("minecraft");

            final boolean result = plugin.onCommand(sender, command, "minecraft", new String[]{});

            assertThat(result).isFalse();
            verify(sender, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("no args -> displays plugin status banner")
        void noArgsShowsStatus() {
            final CommandSender sender = mock(CommandSender.class);
            final PluginDescriptionFile description = mock(PluginDescriptionFile.class);
            doReturn(description).when(plugin).getDescription();
            when(description.getVersion()).thenReturn("1.7.0");

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.GOLD + "  StormtrooperX v1.7.0");
        }

        @Test
        @DisplayName("unknown subcommand -> error message")
        void unknownSubcommand() {
            final CommandSender sender = mock(CommandSender.class);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"foobar"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.RED + "Unknown command. Use /stormtrooperx help for a list of commands.");
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("reload subcommand")
    class Reload {

        @Test
        @DisplayName("without permission -> error message")
        void noPermission() {
            final CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"reload"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
        }

        @Test
        @DisplayName("with permission -> calls reloadConfig() and confirms")
        void hasPermission() {
            final CommandSender sender = mock(CommandSender.class);
            final FileConfiguration mockConfig = mock(FileConfiguration.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
            doNothing().when(plugin).reloadConfig();
            doReturn(mockConfig).when(plugin).getConfig();
            when(mockConfig.getInt("config-version", 2)).thenReturn(3);
            when(mockConfig.getBoolean("debug", false)).thenReturn(false);
            when(mockConfig.contains(anyString())).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"reload"});

            assertThat(result).isTrue();
            verify(plugin).reloadConfig();
            verify(sender).sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
        }

        @Test
        @DisplayName("subcommand matching is case-insensitive")
        void caseInsensitive() {
            final CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"RELOAD"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("optout subcommand (self)")
    class SelfOptOut {

        @Test
        @DisplayName("non-player sender -> error message")
        void nonPlayerSender() {
            final CommandSender sender = mock(CommandSender.class);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.RED + "Only players can use this command without a target!");
        }

        @Test
        @DisplayName("player without permission -> error message")
        void playerNoPermission() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.RED + "You don't have permission to opt-out!");
        }

        @Test
        @DisplayName("not yet opted out -> sets opt-out and confirms")
        void firstTime() {
            final Player sender = mock(Player.class);
            final UUID uuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
            when(sender.getUniqueId()).thenReturn(uuid);
            when(optOutManager.isOptedOut(uuid)).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout"});

            assertThat(result).isTrue();
            verify(optOutManager).setOptOut(uuid, true);
            verify(optOutManager, never()).toggleOptOut(uuid);
            verify(sender).sendMessage(ChatColor.GREEN + "You have opted out of mob accuracy nerfs!");
            verify(sender).sendMessage(ChatColor.YELLOW + "Mobs will shoot at you with normal accuracy.");
        }

        @Test
        @DisplayName("already opted out -> idempotent message, no state change")
        void alreadyOptedOut() {
            final Player sender = mock(Player.class);
            final UUID uuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
            when(sender.getUniqueId()).thenReturn(uuid);
            when(optOutManager.isOptedOut(uuid)).thenReturn(true);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout"});

            assertThat(result).isTrue();
            verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
            verify(sender).sendMessage(ChatColor.YELLOW + "You are already opted out of mob accuracy nerfs.");
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("optin subcommand (self)")
    class SelfOptIn {

        @Test
        @DisplayName("non-player sender -> error message")
        void nonPlayerSender() {
            final CommandSender sender = mock(CommandSender.class);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optin"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.RED + "Only players can use this command without a target!");
        }

        @Test
        @DisplayName("previously opted out -> opts back in and confirms")
        void wasOptedOut() {
            final Player sender = mock(Player.class);
            final UUID uuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
            when(sender.getUniqueId()).thenReturn(uuid);
            when(optOutManager.isOptedOut(uuid)).thenReturn(true);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optin"});

            assertThat(result).isTrue();
            verify(optOutManager).setOptOut(uuid, false);
            verify(sender).sendMessage(ChatColor.GREEN + "You have opted back in to mob accuracy nerfs!");
            verify(sender).sendMessage(ChatColor.YELLOW + "Mobs will now have reduced accuracy when shooting at you.");
        }

        @Test
        @DisplayName("already opted in -> idempotent message, no state change")
        void alreadyOptedIn() {
            final Player sender = mock(Player.class);
            final UUID uuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
            when(sender.getUniqueId()).thenReturn(uuid);
            when(optOutManager.isOptedOut(uuid)).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optin"});

            assertThat(result).isTrue();
            verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
            verify(sender).sendMessage(ChatColor.YELLOW + "You are already opted in to mob accuracy nerfs.");
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("toggle subcommand")
    class Toggle {

        @Test
        @DisplayName("toggle to opted-out -> green confirmation")
        void flipsToOptedOut() {
            final Player sender = mock(Player.class);
            final UUID uuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
            when(sender.getUniqueId()).thenReturn(uuid);
            when(optOutManager.toggleOptOut(uuid)).thenReturn(true);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"toggle"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.GREEN + "You have opted out of mob accuracy nerfs!");
        }

        @Test
        @DisplayName("toggle to opted-in -> green confirmation")
        void flipsToOptedIn() {
            final Player sender = mock(Player.class);
            final UUID uuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
            when(sender.getUniqueId()).thenReturn(uuid);
            when(optOutManager.toggleOptOut(uuid)).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"toggle"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.GREEN + "You have opted back in to mob accuracy nerfs!");
        }

        @Test
        @DisplayName("non-player sender -> error message")
        void nonPlayerSender() {
            final CommandSender sender = mock(CommandSender.class);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"toggle"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.RED + "Only players can use this command!");
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("tab completion")
    class TabComplete {

        @Test
        @DisplayName("wrong command name -> empty list")
        void wrongCommandName() {
            final Command command = mock(Command.class);
            final CommandSender sender = mock(CommandSender.class);
            when(command.getName()).thenReturn("minecraft");

            final List<String> result = plugin.onTabComplete(sender, command, "minecraft", new String[]{""});

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("console with admin perm -> help + reload only")
        void consoleAdminOnly() {
            final CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{""});

            assertThat(result).containsExactly("help", "reload");
        }

        @Test
        @DisplayName("player with admin + optout -> all subcommands, sorted")
        void playerFullPermsAllSubcommands() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{""});

            assertThat(result).containsExactly("help", "optin", "optout", "reload", "toggle");
        }

        @Test
        @DisplayName("player with only optout perm -> help + optin + optout + toggle")
        void playerOptoutOnly() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{""});

            assertThat(result).containsExactly("help", "optin", "optout", "toggle");
        }

        @Test
        @DisplayName("console without any perm -> only help")
        void consoleNoPerms() {
            final CommandSender sender = mock(CommandSender.class);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{""});

            assertThat(result).containsExactly("help");
        }

        @Test
        @DisplayName("console with optout.others -> help + optin + optout (admin-target shape)")
        void consoleWithOptoutOthers() {
            final CommandSender sender = mock(CommandSender.class);
            // Code path under test also probes "stormtrooperx.admin" before reaching the
            // optout.others branch; lenient because Mockito strict mode can't tell that
            // both calls land on the same method with different arguments.
            lenient().when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{""});

            assertThat(result).containsExactly("help", "optin", "optout");
        }

        @Test
        @DisplayName("partial prefix filters to matches")
        void partialPrefix() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{"re"});

            assertThat(result).containsExactly("reload");
        }

        @Test
        @DisplayName("partial prefix is case-insensitive")
        void partialPrefixCaseInsensitive() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(false);
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{"OP"});

            assertThat(result).containsExactly("optin", "optout");
        }

        @Test
        @DisplayName("second arg of /stx reload -> empty")
        void secondArgOfReloadEmpty() {
            final Player sender = mock(Player.class);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{"reload", ""});

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("second arg of /stx optout without admin perm -> empty")
        void secondArgOfOptoutNoAdminPerm() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(false);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{"optout", ""});

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("no matching prefix -> empty")
        void noMatchingPrefix() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{"xyz"});

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("second arg of /stx optout with optout.others -> online players")
        void secondArgOfOptoutOnlinePlayers() {
            final CommandSender sender = mock(CommandSender.class);
            final Server server = mock(Server.class);
            final Player alice = mock(Player.class);
            final Player bob = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
            doReturn(server).when(plugin).getServer();
            final LinkedHashSet<Player> online = new LinkedHashSet<>();
            online.add(alice);
            online.add(bob);
            doReturn(online).when(server).getOnlinePlayers();
            when(alice.getName()).thenReturn("Alice");
            when(bob.getName()).thenReturn("Bob");

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{"optout", ""});

            assertThat(result).containsExactly("Alice", "Bob");
        }

        @Test
        @DisplayName("second arg of /stx optin with prefix filters online players")
        void secondArgOfOptinWithPrefix() {
            final CommandSender sender = mock(CommandSender.class);
            final Server server = mock(Server.class);
            final Player alice = mock(Player.class);
            final Player bob = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
            doReturn(server).when(plugin).getServer();
            final LinkedHashSet<Player> online = new LinkedHashSet<>();
            online.add(alice);
            online.add(bob);
            doReturn(online).when(server).getOnlinePlayers();
            when(alice.getName()).thenReturn("Alice");
            when(bob.getName()).thenReturn("Bob");

            final List<String> result = plugin.onTabComplete(sender, stxCommand(), "stx", new String[]{"optin", "B"});

            assertThat(result).containsExactly("Bob");
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("help subcommand")
    class Help {

        @Test
        @DisplayName("player with optout perm -> self-targeted commands listed")
        void playerSelfCommands() {
            final Player sender = mock(Player.class);
            // sendHelp() checks "stormtrooperx.admin" before "stormtrooperx.optout"; lenient
            // because Mockito strict mode flags the cross-argument match on hasPermission.
            lenient().when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"help"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.GOLD + "  StormtrooperX Commands");
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout" + ChatColor.GRAY + " - Opt yourself out of mob accuracy nerfs");
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optin" + ChatColor.GRAY + " - Opt yourself back in");
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx toggle" + ChatColor.GRAY + " - Flip your opt-out state");
            verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " - Reload configuration");
            verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout <player>" + ChatColor.GRAY + " - Force a player to opt out");
        }

        @Test
        @DisplayName("admin -> all commands listed, including admin-target forms")
        void adminAllCommands() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.optout")).thenReturn(true);
            when(sender.hasPermission("stormtrooperx.admin")).thenReturn(true);
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"help"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " - Reload configuration");
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout <player>" + ChatColor.GRAY + " - Force a player to opt out");
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx optin <player>" + ChatColor.GRAY + " - Force a player to opt in");
        }

        @Test
        @DisplayName("console without perms -> only the public commands")
        void consoleNoPerms() {
            final CommandSender sender = mock(CommandSender.class);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"help"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx" + ChatColor.GRAY + " - Show plugin status");
            verify(sender).sendMessage(ChatColor.YELLOW + "/stormtrooperx help" + ChatColor.GRAY + " - Show this help");
            verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx optout" + ChatColor.GRAY + " - Opt yourself out of mob accuracy nerfs");
            verify(sender, never()).sendMessage(ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " - Reload configuration");
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("admin opt-out/opt-in (target other players)")
    class AdminTarget {

        @Test
        @DisplayName("target online + not opted out -> sets, notifies both")
        void targetOnline() {
            final Player sender = mock(Player.class);
            final Server server = mock(Server.class);
            final Player target = mock(Player.class);
            final UUID targetUuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
            doReturn(server).when(plugin).getServer();
            when(server.getPlayer("Bob")).thenReturn(target);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.getName()).thenReturn("Bob");
            when(optOutManager.isOptedOut(targetUuid)).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout", "Bob"});

            assertThat(result).isTrue();
            verify(optOutManager).setOptOut(targetUuid, true);
            verify(sender).sendMessage(ChatColor.GREEN + "Bob has been opted out of mob accuracy nerfs.");
            verify(target).sendMessage(ChatColor.YELLOW + "An admin has opted you out of mob accuracy nerfs.");
        }

        @Test
        @DisplayName("target offline -> error message, no state change")
        void targetOffline() {
            final Player sender = mock(Player.class);
            final Server server = mock(Server.class);
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
            doReturn(server).when(plugin).getServer();
            when(server.getPlayer("Ghost")).thenReturn(null);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout", "Ghost"});

            assertThat(result).isTrue();
            verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
            verify(sender).sendMessage(ChatColor.RED + "Player 'Ghost' is not online.");
        }

        @Test
        @DisplayName("without optout.others permission -> error message")
        void noPermission() {
            final Player sender = mock(Player.class);
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout", "Bob"});

            assertThat(result).isTrue();
            verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
            verify(sender).sendMessage(ChatColor.RED + "You don't have permission to manage other players' opt-out status!");
        }

        @Test
        @DisplayName("target already opted out -> idempotent, no target message")
        void targetAlreadyOptedOut() {
            final Player sender = mock(Player.class);
            final Server server = mock(Server.class);
            final Player target = mock(Player.class);
            final UUID targetUuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
            doReturn(server).when(plugin).getServer();
            when(server.getPlayer("Bob")).thenReturn(target);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.getName()).thenReturn("Bob");
            when(optOutManager.isOptedOut(targetUuid)).thenReturn(true);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout", "Bob"});

            assertThat(result).isTrue();
            verify(optOutManager, never()).setOptOut(any(UUID.class), anyBoolean());
            verify(sender).sendMessage(ChatColor.YELLOW + "Bob is already opted out.");
            verify(target, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("admin opt-in of opted-out target -> sets, notifies both")
        void optinTargetOptedOut() {
            final Player sender = mock(Player.class);
            final Server server = mock(Server.class);
            final Player target = mock(Player.class);
            final UUID targetUuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
            doReturn(server).when(plugin).getServer();
            when(server.getPlayer("Bob")).thenReturn(target);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.getName()).thenReturn("Bob");
            when(optOutManager.isOptedOut(targetUuid)).thenReturn(true);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optin", "Bob"});

            assertThat(result).isTrue();
            verify(optOutManager).setOptOut(targetUuid, false);
            verify(sender).sendMessage(ChatColor.GREEN + "Bob has been opted back in to mob accuracy nerfs.");
            verify(target).sendMessage(ChatColor.YELLOW + "An admin has opted you in to mob accuracy nerfs.");
        }

        @Test
        @DisplayName("admin targeting self -> no duplicate 'an admin has...' message")
        void targetIsSelf() {
            final Player sender = mock(Player.class);
            final Server server = mock(Server.class);
            final UUID uuid = UUID.randomUUID();
            when(sender.hasPermission("stormtrooperx.optout.others")).thenReturn(true);
            doReturn(server).when(plugin).getServer();
            when(server.getPlayer("self")).thenReturn(sender);
            when(sender.getUniqueId()).thenReturn(uuid);
            when(sender.getName()).thenReturn("self");
            when(optOutManager.isOptedOut(uuid)).thenReturn(false);

            final boolean result = plugin.onCommand(sender, stxCommand(), "stx", new String[]{"optout", "self"});

            assertThat(result).isTrue();
            verify(sender).sendMessage(ChatColor.GREEN + "self has been opted out of mob accuracy nerfs.");
            verify(sender, never()).sendMessage(ChatColor.YELLOW + "An admin has opted you out of mob accuracy nerfs.");
        }
    }
}
