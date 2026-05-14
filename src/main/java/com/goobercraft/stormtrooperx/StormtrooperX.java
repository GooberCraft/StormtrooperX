package com.goobercraft.stormtrooperx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import com.goobercraft.stormtrooperx.scheduler.PluginScheduler;

/**
 * StormtrooperX — a Minecraft plugin that nerfs the accuracy of ranged mobs.
 *
 * <p>JavaPlugin entry point: owns config, the per-entity {@link EntityConfig}
 * map, the {@code /stormtrooperx} command, and the {@code EntityShootBowEvent}
 * handler. A refactor of byteful's original Stormtrooper
 * (https://github.com/byteful/Stormtrooper).</p>
 *
 * @author GooberCraft
 * @author byteful (original implementation)
 */
public final class StormtrooperX extends JavaPlugin implements Listener, TabCompleter {
    private final Logger logger = this.getLogger();

    // volatile + atomic-swap: the event handler reads on Folia regional threads
    // while reload runs on the global thread. loadConfiguration() never mutates
    // a published map — it builds a fresh EnumMap and assigns it in one
    // reference write, so readers always see a consistent snapshot, lock-free.
    private volatile java.util.Map<EntityType, EntityConfig> entityConfigs = new EnumMap<>(EntityType.class);
    private volatile boolean debug = false;
    private DatabaseManager databaseManager;
    private OptOutManager optOutManager;
    private PluginScheduler scheduler;

    // Subcommand pools by required permission, pre-sorted at class load so
    // per-keystroke tab completion can skip Collections.sort.
    private static final List<String> TAB_PUBLIC = List.of("help");
    private static final List<String> TAB_ADMIN = List.of("reload");
    private static final List<String> TAB_OPTOUT = List.of("optin", "optout");
    private static final List<String> TAB_TOGGLE = List.of("toggle");

    /**
     * Configuration for a specific entity type.
     *
     * <p>Package-private so tests in the same package can instantiate it
     * directly without reflection.</p>
     */
    static class EntityConfig {
        private final boolean enabled;
        private final double accuracy;

        EntityConfig(boolean enabled, double accuracy) {
            this.enabled = enabled;
            // Clamp to the valid [0.0, 1.0] range.
            this.accuracy = Math.max(0.0, Math.min(1.0, accuracy));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public double getAccuracy() {
            return accuracy;
        }
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.getServer().getPluginManager().registerEvents(this, this);

        loadConfiguration();

        Objects.requireNonNull(getCommand("stormtrooperx"),
            "Command 'stormtrooperx' missing from plugin.yml").setTabCompleter(this);

        scheduler = PluginScheduler.create(this);

        final String databaseType = getConfig().getString("database.type", "h2");
        databaseManager = new DatabaseManager(logger, getDataFolder(), databaseType, getConfig().getConfigurationSection("database.mysql"));
        databaseManager.initialize();

        optOutManager = new OptOutManager(logger, databaseManager, scheduler, getServer().getMaxPlayers());
        this.getServer().getPluginManager().registerEvents(optOutManager, this);

        registerPlaceholderApiExpansion();

        this.logger.info("========================================");
        this.logger.info("  StormtrooperX v" + getDescription().getVersion());
        this.logger.info("  Successfully enabled!");
        this.logger.info("  Nerfing mob accuracy...");
        this.logger.info("========================================");

        final int pluginId = 27782;
        new Metrics(this, pluginId);

        if (getConfig().getBoolean("check-for-updates", true)) {
            checkForUpdates();
        }
    }

    @Override
    public void onDisable() {
        if (optOutManager != null) {
            optOutManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        this.logger.info("========================================");
        this.logger.info("  StormtrooperX v" + getDescription().getVersion());
        this.logger.info("  Successfully disabled!");
        this.logger.info("========================================");
    }

    /**
     * Registers the PlaceholderAPI expansion if PAPI is installed.
     * Failure to register is logged but never aborts plugin enable — PAPI is a soft dependency.
     */
    private void registerPlaceholderApiExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            final boolean registered = new StormtrooperXExpansion(this, optOutManager).register();
            if (registered) {
                logger.info("PlaceholderAPI detected; registered %stormtrooperx_optout%");
            } else {
                logger.warning("PlaceholderAPI detected but expansion registration returned false");
            }
        } catch (Throwable t) {
            logger.log(java.util.logging.Level.WARNING,
                "Failed to register PlaceholderAPI expansion; placeholders will be unavailable", t);
        }
    }

    private void checkForUpdates() {
        final UpdateChecker updateChecker = new UpdateChecker(this, scheduler, "GooberCraft/StormtrooperX");
        updateChecker.checkForUpdates((comparison, currentVersion, latestVersion) -> {
            // Sink-side CWE-117 guard: both values are validated upstream, but
            // the guard sits across a callback boundary, so re-sanitize locally.
            final String safeCurrent = sanitizeForLog(currentVersion);
            final String safeLatest = sanitizeForLog(latestVersion);
            if (comparison < 0) {
                this.logger.info("========================================");
                this.logger.warning("  A new update is available!");
                this.logger.warning("  Current version: " + safeCurrent);
                this.logger.warning("  Latest version: " + safeLatest);
                this.logger.warning("  Download: https://github.com/GooberCraft/StormtrooperX/releases");
                this.logger.info("========================================");
            } else if (comparison == 0) {
                this.logger.info("You are running the latest version!");
            } else {
                this.logger.info("You are running a development version (" + safeCurrent + ")");
            }
        });
    }

    private void loadConfiguration() {
        final int configVersion = getConfig().getInt("config-version", 2);
        if (configVersion == 2) {
            this.logger.info("Detected config format v2. Migrating to v3...");
            migrateConfigToV3();
            reloadConfig();
        }

        debug = getConfig().getBoolean("debug", false);

        // Build into a staging map and publish in one write — Folia event
        // threads must never see a partially populated map.
        final java.util.Map<EntityType, EntityConfig> staging = new EnumMap<>(EntityType.class);
        loadEntityConfig(staging, "skeleton", EntityType.SKELETON);
        loadEntityConfig(staging, "stray", EntityType.STRAY);
        loadEntityConfig(staging, "bogged", "BOGGED", "1.21+");
        loadEntityConfig(staging, "parched", "PARCHED", "1.21.11+");
        loadEntityConfig(staging, "pillager", "PILLAGER", "1.14+");
        loadEntityConfig(staging, "piglin", "PIGLIN", "1.16+");
        entityConfigs = staging;
    }

    /**
     * Migrates configuration from v2 (per-entity accuracy) to v3 (database configuration added).
     */
    private void migrateConfigToV3() {
        getConfig().set("config-version", 3);

        // v3 adds the database.* block; existing settings are untouched.
        getConfig().set("database.type", "h2");
        getConfig().set("database.mysql.host", "localhost");
        getConfig().set("database.mysql.port", 3306);
        getConfig().set("database.mysql.database", "stormtrooperx");
        getConfig().set("database.mysql.username", "root");
        getConfig().set("database.mysql.password", "");
        getConfig().set("database.mysql.pool.maximum-pool-size", 10);
        getConfig().set("database.mysql.pool.minimum-idle", 2);
        getConfig().set("database.mysql.pool.connection-timeout", 30000);
        getConfig().set("database.mysql.pool.idle-timeout", 600000);
        getConfig().set("database.mysql.pool.max-lifetime", 1800000);

        saveConfig();
        this.logger.info("Config migration to v3 complete! Database configuration added (using H2 by default)");
        this.logger.info("You can configure MySQL database in config.yml if needed");
    }

    /**
     * Loads configuration for a specific entity type into the supplied map.
     *
     * @param target The map to populate (the in-flight reload staging map)
     * @param configKey The key in the config file
     * @param entityType The EntityType enum value
     */
    private void loadEntityConfig(java.util.Map<EntityType, EntityConfig> target, String configKey, EntityType entityType) {
        final String path = "entities." + configKey;

        if (!getConfig().contains(path)) {
            return;
        }

        final boolean enabled = getConfig().getBoolean(path + ".enabled", true);
        final double accuracy = getConfig().getDouble(path + ".accuracy", 0.7);

        if (accuracy < 0.0 || accuracy > 1.0) {
            this.logger.warning(String.format("Entity '%s' has out-of-range accuracy value: %.2f (valid range: 0.0-1.0). Value will be clamped at runtime.", capitalize(configKey), accuracy));
        }

        if (enabled) {
            target.put(entityType, new EntityConfig(true, accuracy));
            this.logger.info(String.format("Entity '%s' will be nerfed! (accuracy: %.2f)", capitalize(configKey), accuracy));
        }
    }

    /**
     * Loads configuration for an entity type that may not exist in all versions.
     *
     * @param target The map to populate (the in-flight reload staging map)
     * @param configKey The key in the config file
     * @param entityTypeName The name of the EntityType enum value
     * @param minVersion The minimum Minecraft version required
     */
    private void loadEntityConfig(java.util.Map<EntityType, EntityConfig> target, String configKey, String entityTypeName, String minVersion) {
        final String path = "entities." + configKey;

        if (!getConfig().contains(path)) {
            return;
        }

        final boolean enabled = getConfig().getBoolean(path + ".enabled", true);

        if (!enabled) {
            return;
        }

        try {
            final EntityType entityType = EntityType.valueOf(entityTypeName);
            final double accuracy = getConfig().getDouble(path + ".accuracy", 0.7);

            if (accuracy < 0.0 || accuracy > 1.0) {
                this.logger.warning(String.format("Entity '%s' has out-of-range accuracy value: %.2f (valid range: 0.0-1.0). Value will be clamped at runtime.", capitalize(configKey), accuracy));
            }

            target.put(entityType, new EntityConfig(true, accuracy));
            this.logger.info(String.format("Entity '%s' will be nerfed! (accuracy: %.2f)", capitalize(configKey), accuracy));
        } catch (IllegalArgumentException e) {
            this.logger.info(String.format("Entity '%s' is not available in this Minecraft version (%s required)", capitalize(configKey), minVersion));
        }
    }

    /**
     * Sanitizes a player name for echoing into chat: replaces control chars and
     * the Bukkit color sign ({@code §}) with {@code ?} and caps length at
     * Mojang's 16-char limit. On offline-mode servers a client can supply
     * arbitrary bytes as a "name", so command args are not safe to echo raw.
     */
    static String sanitizeNameForEcho(String input) {
        if (input == null) {
            return "";
        }
        final int max = Math.min(input.length(), 16);
        final StringBuilder out = new StringBuilder(max);
        for (int i = 0; i < max; i++) {
            final char c = input.charAt(i);
            if (c == ChatColor.COLOR_CHAR || Character.isISOControl(c)) {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Filters a value to version-string characters before it is logged, so it
     * cannot forge or split log entries (CWE-117).
     *
     * <p>The allowlist {@code replaceAll} (negated character class) is
     * deliberate: CodeQL's {@code java/log-injection} query recognizes that
     * form as a sanitizer but not a {@code [\r\n]} denylist. CR/LF and any
     * other unexpected character are dropped.</p>
     *
     * @param value the raw value, may be null
     * @return the value reduced to {@code [A-Za-z0-9._+-]}, or {@code "null"} if null
     */
    static String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.replaceAll("[^A-Za-z0-9._+-]", "");
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Displays the status of an entity in the command output.
     *
     * @param sender The command sender
     * @param entityType The entity type
     * @param displayName The display name
     */
    private void displayEntityStatus(CommandSender sender, EntityType entityType, String displayName) {
        final EntityConfig config = entityConfigs.get(entityType);
        if (config != null && config.isEnabled()) {
            sender.sendMessage(ChatColor.WHITE + "  - " + displayName + ": " + ChatColor.GREEN + "Enabled " + ChatColor.GRAY + "(accuracy: " + String.format("%.2f", config.getAccuracy()) + ")");
        } else {
            sender.sendMessage(ChatColor.WHITE + "  - " + displayName + ": " + ChatColor.RED + "Disabled");
        }
    }

    /**
     * Displays the status of a version-dependent entity in the command output.
     *
     * @param sender The command sender
     * @param entityTypeName The entity type name
     * @param displayName The display name
     * @param versionNote Version requirement note
     */
    private void displayEntityStatus(CommandSender sender, String entityTypeName, String displayName, String versionNote) {
        try {
            final EntityType entityType = EntityType.valueOf(entityTypeName);
            displayEntityStatus(sender, entityType, displayName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.WHITE + "  - " + displayName + ": " + ChatColor.GRAY + "Not available (" + versionNote + ")");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("stormtrooperx")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Permissions are probed admin -> optout -> optout.others to keep the
            // call sequence stable for strict-stubbed tests; pools are pre-sorted
            // so the assembled list needs no Collections.sort.
            final boolean canAdmin = sender.hasPermission("stormtrooperx.admin");
            final boolean canSelfOptout = sender instanceof Player && sender.hasPermission("stormtrooperx.optout");
            final boolean canAdminOptout = sender.hasPermission("stormtrooperx.optout.others");

            final List<String> available = new ArrayList<>(5);
            available.addAll(TAB_PUBLIC);
            if (canSelfOptout || canAdminOptout) {
                available.addAll(TAB_OPTOUT);
            }
            if (canAdmin) {
                available.addAll(TAB_ADMIN);
            }
            if (canSelfOptout) {
                available.addAll(TAB_TOGGLE);
            }

            final List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], available, matches);
            return matches;
        }

        // Second arg: online player names for admin variants of optout/optin
        if (args.length == 2 && sender.hasPermission("stormtrooperx.optout.others")) {
            final String sub = args[0].toLowerCase();
            if (sub.equals("optout") || sub.equals("optin")) {
                final List<String> names = new ArrayList<>();
                for (Player online : getServer().getOnlinePlayers()) {
                    names.add(online.getName());
                }
                Collections.sort(names);
                final List<String> matches = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], names, matches);
                return matches;
            }
        }

        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("stormtrooperx")) {
            return false;
        }

        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        final String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelp(sender);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            case "optout":
                if (args.length >= 2) {
                    handleAdminSet(sender, args[1], true);
                } else {
                    handleSelfSet(sender, true);
                }
                return true;
            case "optin":
                if (args.length >= 2) {
                    handleAdminSet(sender, args[1], false);
                } else {
                    handleSelfSet(sender, false);
                }
                return true;
            case "toggle":
                handleToggle(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /stormtrooperx help for a list of commands.");
                return true;
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.GOLD + "  StormtrooperX v" + getDescription().getVersion());
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Debug Mode: " + ChatColor.WHITE + (debug ? "Enabled" : "Disabled"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Nerfed Entities (Bow Users):");
        displayEntityStatus(sender, EntityType.SKELETON, "Skeleton");
        displayEntityStatus(sender, EntityType.STRAY, "Stray");
        displayEntityStatus(sender, "BOGGED", "Bogged", "1.21+ only");
        displayEntityStatus(sender, "PARCHED", "Parched", "1.21.11+ only");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Nerfed Entities (Crossbow Users):");
        displayEntityStatus(sender, "PILLAGER", "Pillager", "1.14+ only");
        displayEntityStatus(sender, "PIGLIN", "Piglin", "1.16+ only");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/stormtrooperx help" + ChatColor.GRAY + " to see all commands.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.GOLD + "  StormtrooperX Commands");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx" + ChatColor.GRAY + " - Show plugin status");
        sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx help" + ChatColor.GRAY + " - Show this help");
        if (sender.hasPermission("stormtrooperx.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " - Reload configuration");
        }
        if (sender instanceof Player && sender.hasPermission("stormtrooperx.optout")) {
            sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx optout" + ChatColor.GRAY + " - Opt yourself out of mob accuracy nerfs");
            sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx optin" + ChatColor.GRAY + " - Opt yourself back in");
            sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx toggle" + ChatColor.GRAY + " - Flip your opt-out state");
        }
        if (sender.hasPermission("stormtrooperx.optout.others")) {
            sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx optout <player>" + ChatColor.GRAY + " - Force a player to opt out");
            sender.sendMessage(ChatColor.YELLOW + "/stormtrooperx optin <player>" + ChatColor.GRAY + " - Force a player to opt in");
        }
        sender.sendMessage(ChatColor.GRAY + "Aliases: " + ChatColor.WHITE + "/stx, /stormtrooper");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("stormtrooperx.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
            return;
        }
        reloadConfig();
        loadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
    }

    private void handleSelfSet(CommandSender sender, boolean optedOut) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command without a target!");
            return;
        }
        if (!sender.hasPermission("stormtrooperx.optout")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to opt-out!");
            return;
        }

        final boolean current = optOutManager.isOptedOut(player.getUniqueId());

        if (current == optedOut) {
            sender.sendMessage(ChatColor.YELLOW + (optedOut
                ? "You are already opted out of mob accuracy nerfs."
                : "You are already opted in to mob accuracy nerfs."));
            return;
        }

        optOutManager.setOptOut(player.getUniqueId(), optedOut);
        sendOptOutConfirmation(sender, optedOut);
    }

    private void handleAdminSet(CommandSender sender, String targetName, boolean optedOut) {
        if (!sender.hasPermission("stormtrooperx.optout.others")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to manage other players' opt-out status!");
            return;
        }

        final Player target = getServer().getPlayer(targetName);
        if (target == null) {
            // Sanitize: command args bypass Mojang's name regex on offline-mode servers.
            sender.sendMessage(ChatColor.RED + "Player '" + sanitizeNameForEcho(targetName) + "' is not online.");
            return;
        }

        final boolean current = optOutManager.isOptedOut(target.getUniqueId());
        if (current == optedOut) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " is already "
                + (optedOut ? "opted out." : "opted in."));
            return;
        }

        optOutManager.setOptOut(target.getUniqueId(), optedOut);
        sender.sendMessage(ChatColor.GREEN + target.getName()
            + (optedOut ? " has been opted out of mob accuracy nerfs."
                        : " has been opted back in to mob accuracy nerfs."));

        // Tell the target unless the admin targeted themselves
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.YELLOW + "An admin has "
                + (optedOut ? "opted you out of" : "opted you in to")
                + " mob accuracy nerfs.");
        }
    }

    private void handleToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return;
        }
        if (!sender.hasPermission("stormtrooperx.optout")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to opt-out!");
            return;
        }
        final boolean newStatus = optOutManager.toggleOptOut(player.getUniqueId());
        sendOptOutConfirmation(sender, newStatus);
    }

    private void sendOptOutConfirmation(CommandSender sender, boolean optedOut) {
        if (optedOut) {
            sender.sendMessage(ChatColor.GREEN + "You have opted out of mob accuracy nerfs!");
            sender.sendMessage(ChatColor.YELLOW + "Mobs will shoot at you with normal accuracy.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "You have opted back in to mob accuracy nerfs!");
            sender.sendMessage(ChatColor.YELLOW + "Mobs will now have reduced accuracy when shooting at you.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        // Hot path — cache the shooter/type/config locals.
        final org.bukkit.entity.Entity shooter = event.getEntity();
        final EntityType entityType = shooter.getType();
        // volatile read of the published snapshot — Folia regional-thread safe.
        final EntityConfig config = entityConfigs.get(entityType);

        // Cheap null check first: non-configured shots cost almost nothing.
        if (config == null || !config.isEnabled()) {
            return;
        }

        if (debug) {
            logger.info("EntityShootBowEvent -- " + entityType + ": " + event.getProjectile().getVelocity());
        }

        if (shooter instanceof Mob mob) {
            final LivingEntity target = mob.getTarget();

            if (target instanceof Player player) {
                if (optOutManager.isOptedOut(player.getUniqueId())) {
                    if (debug) {
                        logger.info("Skipping nerf for " + player.getName() + " (opted out)");
                    }
                    return;
                }
            }
        }

        // getProjectile() returns Entity in this Spigot API version; cache it.
        final org.bukkit.entity.Entity projectile = event.getProjectile();
        final Vector velocity = projectile.getVelocity();
        // lengthSquared() avoids a sqrt; zero iff the vector is zero-length.
        if (velocity.lengthSquared() == 0) {
            if (debug) {
                logger.info("Skipping projectile with zero velocity from " + entityType);
            }
            return;
        }

        // accuracy is already clamped to [0.0, 1.0] by the EntityConfig ctor.
        ProjectileNerf.perturb(velocity, config.getAccuracy(), Vector::getRandom);
        projectile.setVelocity(velocity);

        if (debug) {
            logger.info("Projectile from '" + entityType + "' launched with modified velocity '" + velocity +
                "' (accuracy: " + String.format("%.2f", config.getAccuracy()) + ")");
        }
    }

}
