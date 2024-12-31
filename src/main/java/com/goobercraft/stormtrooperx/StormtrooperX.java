package com.goobercraft.stormtrooperx;

import java.util.logging.Logger;
import java.util.Set;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class StormtrooperX extends JavaPlugin implements Listener {
    private final Logger logger = this.getLogger();

    private final Set<EntityType> entities = new java.util.HashSet<>();
    private double accuracy = 0.7;
    private boolean debug = false;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

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

        if (getConfig().getBoolean("bogged", true)) {
            this.logger.info("Entity 'Bogged' will be nerfed!");
            entities.add(EntityType.BOGGED);
        }

        this.getServer().getPluginManager().registerEvents(this, this);

        this.logger.info("StormtrooperX has been enabled!");
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (debug) {
            logger.info("EntityShootBowEvent -- " + event.getEntity().getType() + ": " + event.getProjectile().getVelocity());
        }

        if (!entities.contains(event.getEntity().getType())) {
            return;
        }
        else if (event.getEntity().getType() == EntityType.SKELETON && !getConfig().getBoolean("skeleton", true)) {
            return;
        }
        else if (event.getEntity().getType() == EntityType.STRAY && !getConfig().getBoolean("stray", true)) {
            return;
        }
        else if (event.getEntity().getType() == EntityType.BOGGED && !getConfig().getBoolean("bogged", true)) {
            return;
        }

        final Vector vec = event.getProjectile().getVelocity().clone();
        vec.add(Vector.getRandom().multiply(clamp(accuracy, 0, 1)));
        event.getProjectile().setVelocity(vec);

        if (debug) {
            logger.info("Projectile from '" + event.getEntity().getType() + "' launched with modified velocity '" + vec + "'");
        }
    }

}
