package com.aicorelib.brain.goals;

import com.aicorelib.api.Actions;
import com.aicorelib.api.AgentContext;
import com.aicorelib.api.UtilityGoal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Low-health escape: runs to the owner, or away from whatever is hitting us. */
public final class Retreat implements UtilityGoal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aicorelib", "retreat");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public double score(AgentContext ctx) {
        Mob mob = ctx.mob();
        float health = mob.getHealth() / mob.getMaxHealth();
        LivingEntity threat = ctx.perception().nearestHostile();
        if (health <= 0.30F) {
            return 0.95D;
        }
        if (threat != null && ctx.perception().nearestHostileDistance() < 4.0D && health <= 0.55F) {
            return 0.85D;
        }
        // Passive mobs (pig, cow, sheep...) cannot fight back: run instead of dying.
        if (threat != null && !Actions.canFight(mob) && ctx.perception().nearestHostileDistance() < 8.0D) {
            return 0.70D;
        }
        return 0.0D;
    }

    @Override
    public void start(AgentContext ctx) {
        ctx.extras().put("aicorelib:goal/retreat/until", 0L);
    }

    @Override
    public void tick(AgentContext ctx) {
        Mob mob = ctx.mob();
        LivingEntity threat = ctx.perception().nearestHostile();
        Player owner = ctx.perception().owner();

        if (owner != null && ctx.perception().ownerDistance() > 6.0D) {
            Actions.lookAt(mob, owner);
            Actions.moveTo(mob, owner, ctx.speed() * 1.25D);
            return;
        }
        if (threat == null) {
            return;
        }
        Vec3 away = mob.position().subtract(threat.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1, 0, 0);
        }
        Vec3 destination = mob.position().add(away.normalize().scale(8.0D));
        Actions.moveTo(mob, destination, ctx.speed() * 1.25D);
    }

    @Override
    public boolean isFinished(AgentContext ctx) {
        Mob mob = ctx.mob();
        float health = mob.getHealth() / mob.getMaxHealth();
        LivingEntity threat = ctx.perception().nearestHostile();
        boolean safe = threat == null || ctx.perception().nearestHostileDistance() > 12.0D;
        // Keep running for as long as something dangerous is close, even at full health.
        return safe && health > 0.65F;
    }

    @Override
    public void stop(AgentContext ctx) {
        Actions.stop(ctx.mob());
        ctx.extras().remove("aicorelib:goal/retreat/until");
    }
}
