package com.aicorelib.brain.goals;

import com.aicorelib.api.Actions;
import com.aicorelib.api.AgentContext;
import com.aicorelib.api.UtilityGoal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Sticks near the owning player. */
public final class FollowOwner implements UtilityGoal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aicorelib", "follow_owner");

    private static final double COMFORT = 4.0D;
    private static final double MAX_LEASH = 32.0D;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public double score(AgentContext ctx) {
        Player owner = ctx.perception().owner();
        if (owner == null) {
            return 0.0D;
        }
        double distance = ctx.perception().ownerDistance();
        if (distance <= COMFORT) {
            return 0.0D;
        }
        return Math.min(1.0D, (distance - COMFORT) / (MAX_LEASH - COMFORT));
    }

    @Override
    public void start(AgentContext ctx) {
    }

    @Override
    public void tick(AgentContext ctx) {
        Mob mob = ctx.mob();
        Player owner = ctx.perception().owner();
        if (owner == null) {
            return;
        }
        Actions.lookAt(mob, owner);
        if (ctx.perception().ownerDistance() > COMFORT) {
            Actions.moveTo(mob, owner, ctx.speed());
        }
    }

    @Override
    public boolean isFinished(AgentContext ctx) {
        return ctx.perception().owner() == null || ctx.perception().ownerDistance() <= COMFORT * 0.75D;
    }

    @Override
    public void stop(AgentContext ctx) {
        Actions.stop(ctx.mob());
    }

    @Override
    public int minDurationTicks() {
        return 20;
    }
}
