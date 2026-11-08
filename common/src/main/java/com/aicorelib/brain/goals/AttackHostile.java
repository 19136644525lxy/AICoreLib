package com.aicorelib.brain.goals;

import com.aicorelib.api.Actions;
import com.aicorelib.api.AgentContext;
import com.aicorelib.api.AiPreset;
import com.aicorelib.api.UtilityGoal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Chases and hits the nearest hostile within sensing range. */
public final class AttackHostile implements UtilityGoal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aicorelib", "attack_hostile");

    private static final double REACH = 2.6D;
    private static final int ATTACK_COOLDOWN = 20;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public double score(AgentContext ctx) {
        LivingEntity hostile = ctx.perception().nearestHostile();
        if (hostile == null || !Actions.canFight(ctx.mob())) {
            return 0.0D;
        }
        double distance = ctx.perception().nearestHostileDistance();
        return 0.55D + 0.45D * Math.max(0.0D, 1.0D - distance / 16.0D);
    }

    @Override
    public boolean canStart(AgentContext ctx) {
        LivingEntity hostile = ctx.perception().nearestHostile();
        if (hostile == null || !hostile.isAlive() || !Actions.canFight(ctx.mob())) {
            return false;
        }
        AiPreset preset = ctx.preset();
        if (preset != null && !preset.allowPvp() && hostile instanceof net.minecraft.world.entity.player.Player) {
            return false;
        }
        return true;
    }

    @Override
    public void start(AgentContext ctx) {
        ctx.extras().put(key("next_attack"), 0L);
    }

    @Override
    public void tick(AgentContext ctx) {
        Mob mob = ctx.mob();
        LivingEntity target = ctx.perception().nearestHostile();
        if (target == null || !target.isAlive()) {
            return;
        }
        Actions.lookAt(mob, target);
        double distance = mob.distanceTo(target);
        if (distance > REACH) {
            Actions.moveTo(mob, target, ctx.speed());
            return;
        }
        Actions.stop(mob);
        long now = ctx.tick();
        long next = (long) ctx.extras().getOrDefault(key("next_attack"), 0L);
        if (now >= next) {
            Actions.attack(mob, target);
            ctx.extras().put(key("next_attack"), now + ATTACK_COOLDOWN);
        }
    }

    @Override
    public boolean isFinished(AgentContext ctx) {
        LivingEntity target = ctx.perception().nearestHostile();
        return target == null || !target.isAlive();
    }

    @Override
    public void stop(AgentContext ctx) {
        Actions.stop(ctx.mob());
        ctx.extras().remove(key("next_attack"));
    }

    private static String key(String name) {
        return "aicorelib:goal/attack_hostile/" + name;
    }
}
