package com.goobercraft.stormtrooperx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.goobercraft.stormtrooperx.support.TestSupport;

/**
 * Event-handler integration tests for {@code StormtrooperX.onBowShoot}.
 *
 * <p>The pure speed-preserving math lives in {@link ProjectileNerfTest}.
 * The tests here verify the event-routing surface around it: early-return
 * paths, opt-out short-circuit, and that the wiring from event to
 * {@link ProjectileNerf} actually preserves speed end-to-end.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StormtrooperX.onBowShoot — event-handler routing")
class BowShootEventTest {

    private StormtrooperX plugin;
    private EnumMap<EntityType, Object> entityConfigs;

    @Mock
    private OptOutManager optOutManager;

    @BeforeEach
    void setUp() {
        plugin = mock(StormtrooperX.class, CALLS_REAL_METHODS);
        entityConfigs = new EnumMap<>(EntityType.class);
        TestSupport.inject(plugin, "entityConfigs", entityConfigs);
        TestSupport.inject(plugin, "optOutManager", optOutManager);
        TestSupport.inject(plugin, "debug", false);
    }

    private void configureEntity(EntityType type, boolean enabled, double accuracy) {
        entityConfigs.put(type, new StormtrooperX.EntityConfig(enabled, accuracy));
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("early-return paths — getProjectile() must not be called")
    class EarlyReturn {

        @Test
        @DisplayName("entity has no config entry")
        void entityNotConfigured() {
            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final LivingEntity entity = mock(LivingEntity.class);
            when(event.getEntity()).thenReturn(entity);
            when(entity.getType()).thenReturn(EntityType.SKELETON);

            plugin.onBowShoot(event);

            verify(event, never()).getProjectile();
        }

        @Test
        @DisplayName("entity is configured but disabled")
        void entityDisabled() {
            configureEntity(EntityType.SKELETON, false, 0.7);
            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final LivingEntity entity = mock(LivingEntity.class);
            when(event.getEntity()).thenReturn(entity);
            when(entity.getType()).thenReturn(EntityType.SKELETON);

            plugin.onBowShoot(event);

            verify(event, never()).getProjectile();
        }

        @Test
        @DisplayName("target player has opted out")
        void playerOptedOut() {
            configureEntity(EntityType.SKELETON, true, 0.7);
            final UUID playerUuid = UUID.randomUUID();
            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final Mob mob = mock(Mob.class);
            final Player player = mock(Player.class);
            when(event.getEntity()).thenReturn(mob);
            when(mob.getType()).thenReturn(EntityType.SKELETON);
            when(mob.getTarget()).thenReturn(player);
            when(player.getUniqueId()).thenReturn(playerUuid);
            when(optOutManager.isOptedOut(playerUuid)).thenReturn(true);

            plugin.onBowShoot(event);

            verify(event, never()).getProjectile();
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("zero-velocity guard — getProjectile() runs but setVelocity() must not")
    class ZeroVelocityGuard {

        @Test
        @DisplayName("zero-length projectile velocity is left alone")
        void zeroLength() {
            configureEntity(EntityType.SKELETON, true, 0.7);
            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final LivingEntity entity = mock(LivingEntity.class);
            final Entity projectile = mock(Entity.class);
            when(event.getEntity()).thenReturn(entity);
            when(entity.getType()).thenReturn(EntityType.SKELETON);
            when(event.getProjectile()).thenReturn(projectile);
            when(projectile.getVelocity()).thenReturn(new Vector(0, 0, 0));

            plugin.onBowShoot(event);

            verify(projectile, never()).setVelocity(any());
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("velocity-modification paths — setVelocity() must be called")
    class VelocityModification {

        @Test
        @DisplayName("end-to-end: speed is preserved through the event handler -> ProjectileNerf wiring")
        void speedPreservedEndToEnd() {
            configureEntity(EntityType.SKELETON, true, 0.7);
            final Vector originalVelocity = new Vector(1.0, 0.5, 0.5);
            final double originalSpeed = originalVelocity.length();

            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final LivingEntity entity = mock(LivingEntity.class);
            final Entity projectile = mock(Entity.class);
            when(event.getEntity()).thenReturn(entity);
            when(entity.getType()).thenReturn(EntityType.SKELETON);
            when(event.getProjectile()).thenReturn(projectile);
            when(projectile.getVelocity()).thenReturn(originalVelocity);

            plugin.onBowShoot(event);

            final ArgumentCaptor<Vector> captor = ArgumentCaptor.forClass(Vector.class);
            verify(projectile).setVelocity(captor.capture());
            assertThat(captor.getValue().length())
                .as("event-handler must preserve projectile speed")
                .isCloseTo(originalSpeed, within(1e-10));
        }

        @Test
        @DisplayName("player not opted out -> velocity is modified")
        void playerNotOptedOut() {
            configureEntity(EntityType.SKELETON, true, 0.7);
            final UUID playerUuid = UUID.randomUUID();
            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final Mob mob = mock(Mob.class);
            final Player player = mock(Player.class);
            final Entity projectile = mock(Entity.class);
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
        @DisplayName("mob with null target -> velocity is still modified")
        void mobNullTarget() {
            configureEntity(EntityType.SKELETON, true, 0.7);
            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final Mob mob = mock(Mob.class);
            final Entity projectile = mock(Entity.class);
            when(event.getEntity()).thenReturn(mob);
            when(mob.getType()).thenReturn(EntityType.SKELETON);
            when(mob.getTarget()).thenReturn(null);
            when(event.getProjectile()).thenReturn(projectile);
            when(projectile.getVelocity()).thenReturn(new Vector(0.0, 1.0, 0.0));

            plugin.onBowShoot(event);

            verify(projectile).setVelocity(any(Vector.class));
        }

        @Test
        @DisplayName("mob with non-player target -> velocity is still modified")
        void mobNonPlayerTarget() {
            configureEntity(EntityType.SKELETON, true, 0.7);
            final EntityShootBowEvent event = mock(EntityShootBowEvent.class);
            final Mob mob = mock(Mob.class);
            final LivingEntity nonPlayerTarget = mock(LivingEntity.class);
            final Entity projectile = mock(Entity.class);
            when(event.getEntity()).thenReturn(mob);
            when(mob.getType()).thenReturn(EntityType.SKELETON);
            when(mob.getTarget()).thenReturn(nonPlayerTarget);
            when(event.getProjectile()).thenReturn(projectile);
            when(projectile.getVelocity()).thenReturn(new Vector(0.5, 0.5, 0.5));

            plugin.onBowShoot(event);

            verify(projectile).setVelocity(any(Vector.class));
        }
    }
}
