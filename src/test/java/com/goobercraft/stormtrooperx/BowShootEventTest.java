package com.goobercraft.stormtrooperx;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BowShootEventTest {

    private StormtrooperX plugin;
    private Constructor<?> entityConfigConstructor;

    @Mock
    private OptOutManager optOutManager;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(StormtrooperX.class, CALLS_REAL_METHODS);

        // Locate EntityConfig inner class and its constructor
        Class<?> entityConfigClass = null;
        for (Class<?> inner : StormtrooperX.class.getDeclaredClasses()) {
            if ("EntityConfig".equals(inner.getSimpleName())) {
                entityConfigClass = inner;
                break;
            }
        }
        entityConfigConstructor = entityConfigClass.getDeclaredConstructor(boolean.class, double.class);
        entityConfigConstructor.setAccessible(true);

        // Inject empty entityConfigs map — field initializer is skipped by Mockito
        Field entityConfigsField = StormtrooperX.class.getDeclaredField("entityConfigs");
        entityConfigsField.setAccessible(true);
        entityConfigsField.set(plugin, new EnumMap<>(EntityType.class));

        // Inject optOutManager
        Field optOutManagerField = StormtrooperX.class.getDeclaredField("optOutManager");
        optOutManagerField.setAccessible(true);
        optOutManagerField.set(plugin, optOutManager);

        // Keep debug=false — logger is null in the mock; debug paths would NPE
        Field debugField = StormtrooperX.class.getDeclaredField("debug");
        debugField.setAccessible(true);
        debugField.set(plugin, false);
    }

    @SuppressWarnings("unchecked")
    private void addEntityConfig(EntityType type, boolean enabled, double accuracy) throws Exception {
        Field field = StormtrooperX.class.getDeclaredField("entityConfigs");
        field.setAccessible(true);
        ((Map<EntityType, Object>) field.get(plugin)).put(type, entityConfigConstructor.newInstance(enabled, accuracy));
    }

    // -------------------------------------------------------------------------
    // Early-return paths — getProjectile() must never be called
    // -------------------------------------------------------------------------

    @Test
    void testBowShoot_entityNotConfigured_doesNotAccessProjectile() throws Exception {
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        LivingEntity entity = mock(LivingEntity.class);
        when(event.getEntity()).thenReturn(entity);
        when(entity.getType()).thenReturn(EntityType.SKELETON);
        // entityConfigs is empty — no entry for SKELETON

        plugin.onBowShoot(event);

        verify(event, never()).getProjectile();
    }

    @Test
    void testBowShoot_entityDisabled_doesNotAccessProjectile() throws Exception {
        addEntityConfig(EntityType.SKELETON, false, 0.7);

        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        LivingEntity entity = mock(LivingEntity.class);
        when(event.getEntity()).thenReturn(entity);
        when(entity.getType()).thenReturn(EntityType.SKELETON);

        plugin.onBowShoot(event);

        verify(event, never()).getProjectile();
    }

    @Test
    void testBowShoot_playerOptedOut_doesNotAccessProjectile() throws Exception {
        addEntityConfig(EntityType.SKELETON, true, 0.7);
        UUID playerUuid = UUID.randomUUID();

        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        Mob mob = mock(Mob.class);
        Player player = mock(Player.class);
        when(event.getEntity()).thenReturn(mob);
        when(mob.getType()).thenReturn(EntityType.SKELETON);
        when(mob.getTarget()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(optOutManager.isOptedOut(playerUuid)).thenReturn(true);

        plugin.onBowShoot(event);

        verify(event, never()).getProjectile();
    }

    // -------------------------------------------------------------------------
    // Zero-velocity guard — getProjectile() is called but setVelocity() must not be
    // -------------------------------------------------------------------------

    @Test
    void testBowShoot_zeroVelocity_doesNotCallSetVelocity() throws Exception {
        addEntityConfig(EntityType.SKELETON, true, 0.7);

        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        LivingEntity entity = mock(LivingEntity.class);
        Entity projectile = mock(Entity.class);
        when(event.getEntity()).thenReturn(entity);
        when(entity.getType()).thenReturn(EntityType.SKELETON);
        when(event.getProjectile()).thenReturn(projectile);
        when(projectile.getVelocity()).thenReturn(new Vector(0, 0, 0));

        plugin.onBowShoot(event);

        verify(projectile, never()).setVelocity(any());
    }

    // -------------------------------------------------------------------------
    // Velocity-modification paths — setVelocity() must be called
    // -------------------------------------------------------------------------

    @Test
    void testBowShoot_configuredEntity_speedIsPreserved() throws Exception {
        addEntityConfig(EntityType.SKELETON, true, 0.7);
        Vector originalVelocity = new Vector(1.0, 0.5, 0.5);
        double originalSpeed = originalVelocity.length();

        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        LivingEntity entity = mock(LivingEntity.class);
        Entity projectile = mock(Entity.class);
        when(event.getEntity()).thenReturn(entity);
        when(entity.getType()).thenReturn(EntityType.SKELETON);
        when(event.getProjectile()).thenReturn(projectile);
        when(projectile.getVelocity()).thenReturn(originalVelocity);

        plugin.onBowShoot(event);

        ArgumentCaptor<Vector> captor = ArgumentCaptor.forClass(Vector.class);
        verify(projectile).setVelocity(captor.capture());
        assertEquals(originalSpeed, captor.getValue().length(), 1e-10,
                "Projectile speed must be preserved after direction deviation is applied");
    }

    @Test
    void testBowShoot_playerNotOptedOut_modifiesVelocity() throws Exception {
        addEntityConfig(EntityType.SKELETON, true, 0.7);
        UUID playerUuid = UUID.randomUUID();

        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        Mob mob = mock(Mob.class);
        Player player = mock(Player.class);
        Entity projectile = mock(Entity.class);
        when(event.getEntity()).thenReturn(mob);
        when(mob.getType()).thenReturn(EntityType.SKELETON);
        when(mob.getTarget()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(optOutManager.isOptedOut(playerUuid)).thenReturn(false);
        when(event.getProjectile()).thenReturn(projectile);
        when(projectile.getVelocity()).thenReturn(new Vector(1.0, 0.0, 0.0));

        plugin.onBowShoot(event);

        verify(projectile).setVelocity(any(Vector.class));
    }

    @Test
    void testBowShoot_mobWithNullTarget_modifiesVelocity() throws Exception {
        addEntityConfig(EntityType.SKELETON, true, 0.7);

        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        Mob mob = mock(Mob.class);
        Entity projectile = mock(Entity.class);
        when(event.getEntity()).thenReturn(mob);
        when(mob.getType()).thenReturn(EntityType.SKELETON);
        when(mob.getTarget()).thenReturn(null);
        when(event.getProjectile()).thenReturn(projectile);
        when(projectile.getVelocity()).thenReturn(new Vector(0.0, 1.0, 0.0));

        plugin.onBowShoot(event);

        verify(projectile).setVelocity(any(Vector.class));
    }

    @Test
    void testBowShoot_mobWithNonPlayerTarget_modifiesVelocity() throws Exception {
        addEntityConfig(EntityType.SKELETON, true, 0.7);

        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        Mob mob = mock(Mob.class);
        LivingEntity nonPlayerTarget = mock(LivingEntity.class);
        Entity projectile = mock(Entity.class);
        when(event.getEntity()).thenReturn(mob);
        when(mob.getType()).thenReturn(EntityType.SKELETON);
        when(mob.getTarget()).thenReturn(nonPlayerTarget);
        when(event.getProjectile()).thenReturn(projectile);
        when(projectile.getVelocity()).thenReturn(new Vector(0.5, 0.5, 0.5));

        plugin.onBowShoot(event);

        verify(projectile).setVelocity(any(Vector.class));
    }
}
