package com.goobercraft.stormtrooperx;

import java.util.logging.Logger;

import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * StormtrooperX - A Minecraft plugin that nerfs the accuracy of ranged mobs.
 *
 * This is a refactored and enhanced version of the original Stormtrooper plugin
 * by byteful (https://github.com/byteful/Stormtrooper).
 *
 * Enhancements include:
 * - Support for multiple mob types (Skeleton, Stray, Bogged, Pillager, Piglin)
 * - Version compatibility (1.13+)
 * - Arrow and crossbow bolt speed preservation (only direction is modified)
 * - Reload command and improved UX
 * - Per-entity configuration
 * - Player opt-out system
 *
 * @author GooberCraft
 * @author byteful (original implementation)
 */
public final class StormtrooperX extends JavaPlugin implements Listener {
    private final Logger logger = this.getLogger();

    private final java.util.Map<EntityType, EntityConfig> entityConfigs = new java.util.HashMap<>();
    private boolean debug = false;
    private DatabaseManager databaseManager;
    private OptOutManager optOutManager;

    /**
     * Configuration for a specific entity type.
     */
    private static class EntityConfig {
        private final boolean enabled;
        private final double accuracy;

        EntityConfig(boolean enabled, double accuracy) {
            this.enabled = enabled;
            // Defensive: clamp accuracy to valid range [0.0, 1.0] at construction time
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

        // Initialize database
        databaseManager = new DatabaseManager(logger, getDataFolder());
        databaseManager.initialize();

        // Initialize opt-out manager with async support and server max players for cache sizing
        optOutManager = new OptOutManager(logger, databaseManager, this, getServer().getMaxPlayers());
        this.getServer().getPluginManager().registerEvents(optOutManager, this);

        loadConfiguration();

        this.logger.info("========================================");
        this.logger.info("  StormtrooperX v" + getDescription().getVersion());
        this.logger.info("  Successfully enabled!");
        this.logger.info("  Nerfing mob accuracy...");
        this.logger.info("========================================");

        // Initialize bStats metrics
        final int pluginId = 27782;
        new Metrics(this, pluginId);

        // Check for updates
        if (getConfig().getBoolean("check-for-updates", true)) {
            checkForUpdates();
        }
    }

    @Override
    public void onDisable() {
        // Shutdown opt-out manager
        if (optOutManager != null) {
            optOutManager.shutdown();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        this.logger.info("========================================");
        this.logger.info("  StormtrooperX v" + getDescription().getVersion());
        this.logger.info("  Successfully disabled!");
        this.logger.info("========================================");
    }

    private void checkForUpdates() {
        final UpdateChecker updateChecker = new UpdateChecker(this, "GooberCraft/StormtrooperX");
        updateChecker.checkForUpdates((comparison, currentVersion, latestVersion) -> {
            if (comparison < 0) {
                this.logger.info("========================================");
                this.logger.warning("  A new update is available!");
                this.logger.warning("  Current version: " + currentVersion);
                this.logger.warning("  Latest version: " + latestVersion);
                this.logger.warning("  Download: https://github.com/GooberCraft/StormtrooperX/releases");
                this.logger.info("========================================");
            } else if (comparison == 0) {
                this.logger.info("You are running the latest version!");
            } else {
                this.logger.info("You are running a development version (" + currentVersion + ")");
            }
        });
    }

    private void loadConfiguration() {
        entityConfigs.clear();

        // Check config version and migrate if needed
        final int configVersion = getConfig().getInt("config-version", 1);
        if (configVersion < 2) {
            this.logger.info("Detected old config format (v1). Migrating to v2...");
            migrateConfigToV2();
            reloadConfig(); // Reload after migration
        }

        debug = getConfig().getBoolean("debug", false);

        // Load per-entity configurations
        loadEntityConfig("skeleton", EntityType.SKELETON);
        loadEntityConfig("stray", EntityType.STRAY);
        loadEntityConfig("bogged", "BOGGED", "1.21+");
        loadEntityConfig("pillager", "PILLAGER", "1.14+");
        loadEntityConfig("piglin", "PIGLIN", "1.16+");
    }

    /**
     * Migrates configuration from v1 (global accuracy) to v2 (per-entity accuracy).
     */
    private void migrateConfigToV2() {
        // Read old format values
        final double globalAccuracy = getConfig().getDouble("accuracy", 0.7);
        final boolean skeletonEnabled = getConfig().getBoolean("skeleton", true);
        final boolean strayEnabled = getConfig().getBoolean("stray", true);
        final boolean boggedEnabled = getConfig().getBoolean("bogged", true);
        final boolean pillagerEnabled = getConfig().getBoolean("pillager", true);
        final boolean piglinEnabled = getConfig().getBoolean("piglin", true);
        final boolean checkUpdates = getConfig().getBoolean("check-for-updates", true);
        final boolean debugMode = getConfig().getBoolean("debug", false);

        // Clear old config
        getConfig().set("accuracy", null);
        getConfig().set("skeleton", null);
        getConfig().set("stray", null);
        getConfig().set("bogged", null);
        getConfig().set("pillager", null);
        getConfig().set("piglin", null);

        // Set new format
        getConfig().set("config-version", 2);

        // Migrate entities with per-entity accuracy
        getConfig().set("entities.skeleton.enabled", skeletonEnabled);
        getConfig().set("entities.skeleton.accuracy", globalAccuracy);
        getConfig().set("entities.stray.enabled", strayEnabled);
        getConfig().set("entities.stray.accuracy", globalAccuracy);
        getConfig().set("entities.bogged.enabled", boggedEnabled);
        getConfig().set("entities.bogged.accuracy", globalAccuracy);
        getConfig().set("entities.pillager.enabled", pillagerEnabled);
        getConfig().set("entities.pillager.accuracy", globalAccuracy);
        getConfig().set("entities.piglin.enabled", piglinEnabled);
        getConfig().set("entities.piglin.accuracy", globalAccuracy);

        // Preserve other settings
        getConfig().set("check-for-updates", checkUpdates);
        getConfig().set("debug", debugMode);

        // Save migrated config
        saveConfig();
        this.logger.info("Config migration complete! All entities now use accuracy: " + globalAccuracy);
        this.logger.info("You can now configure individual accuracy per entity in config.yml");
    }

    /**
     * Loads configuration for a specific entity type.
     *
     * @param configKey The key in the config file
     * @param entityType The EntityType enum value
     */
    private void loadEntityConfig(String configKey, EntityType entityType) {
        final String path = "entities." + configKey;

        if (!getConfig().contains(path)) {
            return;
        }

        final boolean enabled = getConfig().getBoolean(path + ".enabled", true);
        final double accuracy = getConfig().getDouble(path + ".accuracy", 0.7);

        // Warn if accuracy is out of valid range
        if (accuracy < 0.0 || accuracy > 1.0) {
            this.logger.warning(String.format("Entity '%s' has out-of-range accuracy value: %.2f (valid range: 0.0-1.0). Value will be clamped at runtime.", capitalize(configKey), accuracy));
        }

        if (enabled) {
            entityConfigs.put(entityType, new EntityConfig(true, accuracy));
            this.logger.info(String.format("Entity '%s' will be nerfed! (accuracy: %.2f)", capitalize(configKey), accuracy));
        }
    }

    /**
     * Loads configuration for an entity type that may not exist in all versions.
     *
     * @param configKey The key in the config file
     * @param entityTypeName The name of the EntityType enum value
     * @param minVersion The minimum Minecraft version required
     */
    private void loadEntityConfig(String configKey, String entityTypeName, String minVersion) {
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

            // Warn if accuracy is out of valid range
            if (accuracy < 0.0 || accuracy > 1.0) {
                this.logger.warning(String.format("Entity '%s' has out-of-range accuracy value: %.2f (valid range: 0.0-1.0). Value will be clamped at runtime.", capitalize(configKey), accuracy));
            }

            entityConfigs.put(entityType, new EntityConfig(true, accuracy));
            this.logger.info(String.format("Entity '%s' will be nerfed! (accuracy: %.2f)", capitalize(configKey), accuracy));
        } catch (IllegalArgumentException e) {
            this.logger.info(String.format("Entity '%s' is not available in this Minecraft version (%s required)", capitalize(configKey), minVersion));
        }
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("stormtrooperx")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "========================================");
                sender.sendMessage(ChatColor.GOLD + "  StormtrooperX v" + getDescription().getVersion());
                sender.sendMessage(ChatColor.GOLD + "========================================");
                sender.sendMessage(ChatColor.YELLOW + "Debug Mode: " + ChatColor.WHITE + (debug ? "Enabled" : "Disabled"));
                sender.sendMessage("");
                sender.sendMessage(ChatColor.YELLOW + "Nerfed Entities (Bow Users):");
                displayEntityStatus(sender, EntityType.SKELETON, "Skeleton");
                displayEntityStatus(sender, EntityType.STRAY, "Stray");
                displayEntityStatus(sender, "BOGGED", "Bogged", "1.21+ only");

                sender.sendMessage("");
                sender.sendMessage(ChatColor.YELLOW + "Nerfed Entities (Crossbow Users):");
                displayEntityStatus(sender, "PILLAGER", "Pillager", "1.14+ only");
                displayEntityStatus(sender, "PIGLIN", "Piglin", "1.16+ only");

                sender.sendMessage("");
                sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " to reload the configuration.");
                sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/stormtrooperx optout" + ChatColor.GRAY + " to toggle personal opt-out.");
                return true;
            }

            // Validate args[0] exists before accessing
            final String subCommand = args[0].toLowerCase();

            if (subCommand.equals("reload")) {
                if (!sender.hasPermission("stormtrooperx.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
                    return true;
                }

                reloadConfig();
                loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
                return true;
            }

            if (subCommand.equals("optout") || subCommand.equals("toggle")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }

                if (!sender.hasPermission("stormtrooperx.optout")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to opt-out!");
                    return true;
                }

                final Player player = (Player) sender;
                final boolean newStatus = optOutManager.toggleOptOut(player.getUniqueId());

                if (newStatus) {
                    sender.sendMessage(ChatColor.GREEN + "You have opted out of mob accuracy nerfs!");
                    sender.sendMessage(ChatColor.YELLOW + "Mobs will shoot at you with normal accuracy.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "You have opted back in to mob accuracy nerfs!");
                    sender.sendMessage(ChatColor.YELLOW + "Mobs will now have reduced accuracy when shooting at you.");
                }
                return true;
            }

            // Unknown subcommand
            sender.sendMessage(ChatColor.RED + "Unknown command. Use /stormtrooperx for help.");
            return true;
        }

        return false;
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (debug) {
            logger.info("EntityShootBowEvent -- " + event.getEntity().getType() + ": " + event.getProjectile().getVelocity());
        }

        // Get entity configuration
        final EntityType entityType = event.getEntity().getType();
        final EntityConfig config = entityConfigs.get(entityType);

        // Only process entities that are configured and enabled
        if (config == null || !config.isEnabled()) {
            return;
        }

        // Check if the target player has opted out
        if (event.getEntity() instanceof Mob) {
            final Mob mob = (Mob) event.getEntity();
            final LivingEntity target = mob.getTarget();

            if (target instanceof Player) {
                final Player player = (Player) target;
                if (optOutManager.isOptedOut(player.getUniqueId())) {
                    if (debug) {
                        logger.info("Skipping nerf for " + player.getName() + " (opted out)");
                    }
                    return;
                }
            }
        }

        // Get projectile velocity and preserve original speed
        final Vector velocity = event.getProjectile().getVelocity();
        final double originalSpeed = velocity.length();

        // Generate random deviation scaled by accuracy factor
        // Note: accuracy is already clamped to [0.0, 1.0] in EntityConfig constructor
        final Vector deviation = Vector.getRandom();
        deviation.multiply(config.getAccuracy());

        // Apply deviation and restore original speed
        velocity.add(deviation);
        velocity.normalize();
        velocity.multiply(originalSpeed);
        event.getProjectile().setVelocity(velocity);

        if (debug) {
            logger.info("Projectile from '" + event.getEntity().getType() + "' launched with modified velocity '" + velocity +
                "' (accuracy: " + String.format("%.2f", config.getAccuracy()) + ")");
        }
    }

}
