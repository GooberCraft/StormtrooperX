package com.goobercraft.stormtrooperx;

import java.util.logging.Logger;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
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
 * - Support for multiple mob types (Skeleton, Stray, Bogged)
 * - Version compatibility (1.13+)
 * - Arrow speed preservation (only direction is modified)
 * - Reload command and improved UX
 * - Per-entity configuration
 *
 * @author GooberCraft
 * @author byteful (original implementation)
 */
public final class StormtrooperX extends JavaPlugin implements Listener {
    private final Logger logger = this.getLogger();

    private final Set<EntityType> entities = new java.util.HashSet<>();
    private double accuracy = 0.7;
    private boolean debug = false;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.getServer().getPluginManager().registerEvents(this, this);

        loadConfiguration();

        this.logger.info("========================================");
        this.logger.info("  StormtrooperX v" + getDescription().getVersion());
        this.logger.info("  Successfully enabled!");
        this.logger.info("  Nerfing mob accuracy...");
        this.logger.info("========================================");
    }

    @Override
    public void onDisable() {
        this.logger.info("========================================");
        this.logger.info("  StormtrooperX v" + getDescription().getVersion());
        this.logger.info("  Successfully disabled!");
        this.logger.info("========================================");
    }

    private void loadConfiguration() {
        entities.clear();

        accuracy = getConfig().getDouble("accuracy", 0.7);
        debug = getConfig().getBoolean("debug", false);

        if (getConfig().getBoolean("skeleton", true)) {
            this.logger.info("Entity 'Skeleton' will be nerfed!");
            entities.add(EntityType.SKELETON);
        }

        if (getConfig().getBoolean("stray", true)) {
            this.logger.info("Entity 'Stray' will be nerfed!");
            entities.add(EntityType.STRAY);
        }

        // BOGGED only exists in 1.21+, handle gracefully for older versions
        if (getConfig().getBoolean("bogged", true)) {
            try {
                EntityType boggedType = EntityType.valueOf("BOGGED");
                this.logger.info("Entity 'Bogged' will be nerfed!");
                entities.add(boggedType);
            } catch (IllegalArgumentException e) {
                this.logger.info("Entity 'Bogged' is not available in this Minecraft version (1.21+ required)");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("stormtrooperx")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "========================================");
                sender.sendMessage(ChatColor.GOLD + "  StormtrooperX v" + getDescription().getVersion());
                sender.sendMessage(ChatColor.GOLD + "========================================");
                sender.sendMessage(ChatColor.YELLOW + "Accuracy: " + ChatColor.WHITE + String.format("%.2f", accuracy));
                sender.sendMessage(ChatColor.YELLOW + "Debug Mode: " + ChatColor.WHITE + (debug ? "Enabled" : "Disabled"));
                sender.sendMessage("");
                sender.sendMessage(ChatColor.YELLOW + "Nerfed Entities:");
                sender.sendMessage(ChatColor.WHITE + "  - Skeleton: " + (getConfig().getBoolean("skeleton", true) ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
                sender.sendMessage(ChatColor.WHITE + "  - Stray: " + (getConfig().getBoolean("stray", true) ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

                // Check if BOGGED exists in this version
                try {
                    EntityType.valueOf("BOGGED");
                    sender.sendMessage(ChatColor.WHITE + "  - Bogged: " + (getConfig().getBoolean("bogged", true) ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.WHITE + "  - Bogged: " + ChatColor.GRAY + "Not available (1.21+ only)");
                }
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/stormtrooperx reload" + ChatColor.GRAY + " to reload the configuration.");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("stormtrooperx.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
                    return true;
                }

                reloadConfig();
                loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
                return true;
            }
        }

        return false;
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (debug) {
            logger.info("EntityShootBowEvent -- " + event.getEntity().getType() + ": " + event.getProjectile().getVelocity());
        }

        // Only process entities that are in our configured set
        if (!entities.contains(event.getEntity().getType())) {
            return;
        }

        final Vector vec = event.getProjectile().getVelocity().clone();
        final double originalSpeed = vec.length();

        // Add random deviation to the direction
        vec.add(Vector.getRandom().multiply(clamp(accuracy, 0, 1)));

        // Preserve the original arrow speed
        vec.normalize().multiply(originalSpeed);
        event.getProjectile().setVelocity(vec);

        if (debug) {
            logger.info("Projectile from '" + event.getEntity().getType() + "' launched with modified velocity '" + vec + "'");
        }
    }

}
