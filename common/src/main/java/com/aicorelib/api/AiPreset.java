package com.aicorelib.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A named bundle of goals and tuning parameters.
 *
 * <p>M1 ships these in code; M2 loads the same shape from
 * {@code data/<ns>/aicorelib/presets/*.json} and lets data packs override the built-ins.</p>
 */
public record AiPreset(
        ResourceLocation id,
        String displayName,
        List<GoalWeight> goals,
        double followRange,
        double speed,
        boolean allowPvp,
        int decisionIntervalTicks) {

    /** A goal plus the weight its score is multiplied by inside this preset. */
    public record GoalWeight(ResourceLocation goalId, double weight) {
    }

    public AiPreset {
        goals = List.copyOf(goals);
        if (decisionIntervalTicks <= 0) {
            decisionIntervalTicks = 10;
        }
    }
}
