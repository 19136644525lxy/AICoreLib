package com.aicorelib.brain;

import com.aicorelib.agent.AgentHandle;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The insert-only hook into the vanilla goal selector.
 *
 * <p>This goal does no thinking. Its only job is to claim {@code MOVE}, {@code LOOK} and
 * {@code TARGET} while the brain wants control, so vanilla goals with equal or lower
 * priority give way. We never claim {@code JUMP}: {@code FloatGoal} needs it to keep the
 * mob from drowning, and stealing it is the classic way AI mods kill their agents.</p>
 *
 * <p>When {@link #canUse()} returns {@code false} the vanilla selector behaves exactly as
 * before, which is what makes {@code /aicorelib remove} a true undo.</p>
 */
public final class AgentGoal extends Goal {
    private final AgentHandle handle;

    public AgentGoal(AgentHandle handle) {
        this.handle = handle;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return handle.remoteActive() || handle.brain().wantsControl();
    }

    @Override
    public boolean canContinueToUse() {
        return handle.remoteActive() || handle.brain().wantsControl();
    }

    @Override
    public void start() {
        handle.mob().getNavigation().stop();
    }

    @Override
    public void stop() {
        handle.mob().getNavigation().stop();
    }
}
