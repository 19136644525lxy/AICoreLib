package com.aicorelib.brain;

import com.aicorelib.api.AgentContext;
import com.aicorelib.api.Perception;
import com.aicorelib.api.Sensor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * The default sensing pass: finds the owner, the nearest hostile, and remembers who
 * punched us last.
 *
 * <p>Hostile means {@link Enemy} and {@link Mob#canAttack attackable}. We deliberately do
 * not treat every {@code LivingEntity} as a target, otherwise guards would start brawls
 * with item frames and armour stands.</p>
 */
public final class EntitySensor implements Sensor {
    private final double radius;

    public EntitySensor(double radius) {
        this.radius = radius;
    }

    @Override
    public void sample(AgentContext ctx) {
        Mob mob = ctx.mob();
        Perception p = ctx.perception();

        sampleOwner(ctx, mob, p);
        sampleHostile(mob, p);
        sampleRecentHurt(mob, p);
    }

    private void sampleOwner(AgentContext ctx, Mob mob, Perception p) {
        UUID ownerId = ctx.ownerId();
        if (ownerId == null || mob.getServer() == null) {
            p.setOwner(null, Double.MAX_VALUE, false);
            return;
        }
        ServerPlayer owner = mob.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != mob.level()) {
            p.setOwner(null, Double.MAX_VALUE, false);
            return;
        }
        double distance = Math.sqrt(mob.distanceToSqr(owner));
        p.setOwner(owner, distance, mob.getSensing().hasLineOfSight(owner));
    }

    private void sampleHostile(Mob mob, Perception p) {
        AABB box = mob.getBoundingBox().inflate(radius);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != mob && e.isAlive() && e instanceof Enemy)) {
            if (!mob.canAttack(candidate)) {
                continue;
            }
            Player owner = p.owner();
            if (owner != null && candidate == owner) {
                continue;
            }
            double distance = mob.distanceTo(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        p.setNearestHostile(best, best == null ? Double.MAX_VALUE : bestDistance);
    }

    private void sampleRecentHurt(Mob mob, Perception p) {
        LivingEntity attacker = mob.getLastHurtByMob();
        if (attacker != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100) {
            p.setLastHurt(mob.level().getGameTime(), attacker.getUUID());
        }
    }

    @Nullable
    public LivingEntity nearest(@Nullable LivingEntity e) {
        return e;
    }
}
