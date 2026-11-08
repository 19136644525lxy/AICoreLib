package com.aicorelib.brain.goals;

import com.aicorelib.api.Actions;
import com.aicorelib.api.AgentContext;
import com.aicorelib.api.UtilityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Wander around the agent's home point.
 *
 * <p>Also serves as the safety net: {@link #claimsControl()} is {@code false}, so while
 * patrolling the vanilla goal selector keeps running and the mob still avoids water,
 * cliffs and lava on its own.</p>
 */
public final class Patrol implements UtilityGoal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aicorelib", "patrol");

    private static final int NEXT_TARGET_TICKS = 120;
    private static final double HOME_RADIUS = 8.0D;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public double score(AgentContext ctx) {
        if (ctx.perception().nearestHostile() != null) {
            return 0.0D;
        }
        return ctx.perception().owner() == null ? 0.20D : 0.08D;
    }

    @Override
    public boolean claimsControl() {
        return false;
    }

    @Override
    public void start(AgentContext ctx) {
        pickTarget(ctx);
    }

    @Override
    public void tick(AgentContext ctx) {
        Mob mob = ctx.mob();
        long next = getLong(ctx, "next_target", 0L);
        if (ctx.tick() >= next || mob.getNavigation().isDone()) {
            pickTarget(ctx);
        }
    }

    private void pickTarget(AgentContext ctx) {
        Mob mob = ctx.mob();
        Vec3 home = ctx.perception().home();
        double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 2.0D + mob.getRandom().nextDouble() * (HOME_RADIUS - 2.0D);
        BlockPos target = BlockPos.containing(
                home.x + Math.cos(angle) * radius,
                home.y,
                home.z + Math.sin(angle) * radius);
        Actions.moveTo(mob, Vec3.atBottomCenterOf(target), ctx.speed() * 0.6D);
        ctx.extras().put("aicorelib:goal/patrol/next_target", ctx.tick() + NEXT_TARGET_TICKS);
    }

    @Override
    public void stop(AgentContext ctx) {
        Actions.stop(ctx.mob());
    }

    private static long getLong(AgentContext ctx, String name, long fallback) {
        Object value = ctx.extras().get("aicorelib:goal/patrol/" + name);
        return value instanceof Long l ? l : fallback;
    }
}
