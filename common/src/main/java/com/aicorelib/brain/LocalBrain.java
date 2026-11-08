package com.aicorelib.brain;

import com.aicorelib.api.AgentContext;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.AiPreset;
import com.aicorelib.api.UtilityGoal;

import org.jetbrains.annotations.Nullable;

/**
 * The offline brain: a weighted utility scorer.
 *
 * <p>Runs on the server thread every tick, but only re-evaluates candidates every
 * {@code decisionIntervalTicks}. Switching away from a running goal needs a clear margin
 * (hysteresis) so agents do not twitch between two similar urges.</p>
 */
public final class LocalBrain {
    private static final double SWITCH_MARGIN = 0.10D;

    private final AgentContext ctx;
    private @Nullable UtilityGoal active;
    private int ticksRunning;
    private boolean decidedOnce;
    private long lastDecision;
    private boolean suspended;

    public LocalBrain(AgentContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Hands the body over to the remote brain (or takes it back).
     *
     * <p>While suspended the utility loop does not run at all, so the two brains never
     * fight over navigation. Suspending stops the running goal immediately; resuming lets
     * the next tick pick a goal from scratch.</p>
     */
    public void setSuspended(boolean value) {
        if (suspended == value) {
            return;
        }
        suspended = value;
        if (value) {
            stopActive();
        } else {
            decidedOnce = false;
        }
    }

    public boolean suspended() {
        return suspended;
    }

    @Nullable
    public UtilityGoal activeGoal() {
        return active;
    }

    /** Whether the brain currently wants to own movement/look/target. */
    public boolean wantsControl() {
        return active != null && active.claimsControl() && !active.isFinished(ctx);
    }

    public void tick(long now) {
        ctx.setTick(now);
        if (suspended) {
            return;
        }
        AiPreset preset = ctx.preset();
        if (preset == null) {
            stopActive();
            return;
        }

        if (active != null) {
            active.tick(ctx);
            ticksRunning++;
            if (active.isFinished(ctx)) {
                stopActive();
            }
        }

        if (!decidedOnce || now - lastDecision >= preset.decisionIntervalTicks()) {
            decidedOnce = true;
            lastDecision = now;
            decide(preset);
        }
    }

    /** Stops whatever is running; called when the agent is released. */
    public void stopActiveGoal() {
        stopActive();
    }

    private void decide(AiPreset preset) {
        UtilityGoal best = null;
        double bestScore = 0.0D;

        for (AiPreset.GoalWeight weight : preset.goals()) {
            UtilityGoal goal = AiApi.goal(weight.goalId());
            if (goal == null || !goal.canStart(ctx)) {
                continue;
            }
            double score = goal.score(ctx) * weight.weight();
            if (score > bestScore) {
                bestScore = score;
                best = goal;
            }
        }

        if (best == null) {
            stopActive();
            return;
        }
        if (best == active) {
            return;
        }
        if (active != null) {
            boolean locked = ticksRunning < active.minDurationTicks();
            double currentScore = active.score(ctx) * weightOf(preset, active);
            if (locked || bestScore <= currentScore + SWITCH_MARGIN) {
                return;
            }
        }
        start(best);
    }

    private void start(UtilityGoal goal) {
        stopActive();
        active = goal;
        ticksRunning = 0;
        goal.start(ctx);
    }

    private void stopActive() {
        if (active != null) {
            active.stop(ctx);
            active = null;
            ticksRunning = 0;
        }
    }

    private static double weightOf(AiPreset preset, UtilityGoal goal) {
        for (AiPreset.GoalWeight weight : preset.goals()) {
            if (weight.goalId().equals(goal.id())) {
                return weight.weight();
            }
        }
        return 0.0D;
    }
}
