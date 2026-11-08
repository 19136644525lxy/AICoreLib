package com.aicorelib.api;

import net.minecraft.resources.ResourceLocation;

/**
 * One candidate behaviour in the utility AI.
 *
 * <p>Every {@code decisionIntervalTicks} the brain asks each goal for a {@link #score}.
 * The highest score wins, subject to hysteresis, and is then ticked until it
 * {@link #isFinished finishes} or a much better candidate appears.</p>
 *
 * <p>Goals are singletons: one instance serves every agent, so implementations must be
 * stateless and keep per-agent data in {@link AgentContext}.</p>
 */
public interface UtilityGoal {
    ResourceLocation id();

    /**
     * @return urgency in {@code [0, 1]}; 0 means "not interested". Called on the server thread.
     */
    double score(AgentContext ctx);

    /** Extra gate applied before a goal may start even with a high score. */
    default boolean canStart(AgentContext ctx) {
        return true;
    }

    void start(AgentContext ctx);

    void tick(AgentContext ctx);

    default boolean isFinished(AgentContext ctx) {
        return false;
    }

    default void stop(AgentContext ctx) {
    }

    /**
     * Whether this goal wants exclusive control of movement/look/target.
     *
     * <p>Returning {@code false} lets the vanilla goal selector keep doing its thing —
     * that is how an idle agent still wanders around instead of standing frozen.</p>
     */
    default boolean claimsControl() {
        return true;
    }

    /** Minimum ticks this goal stays active once started, to avoid decision flicker. */
    default int minDurationTicks() {
        return 0;
    }
}
