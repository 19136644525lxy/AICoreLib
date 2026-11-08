package com.aicorelib.preset;

import com.aicorelib.AICoreLib;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.AiPreset;
import com.aicorelib.brain.EntitySensor;
import com.aicorelib.brain.goals.AttackHostile;
import com.aicorelib.brain.goals.FollowOwner;
import com.aicorelib.brain.goals.Patrol;
import com.aicorelib.brain.goals.Retreat;

import java.util.List;

/**
 * The presets and goals shipped inside the library.
 *
 * <p>M2 replaces these with data pack JSON; until then this is the only place the
 * built-ins are defined, so adding a goal to the default guard is a one-line change.</p>
 */
public final class Presets {
    public static final double SENSOR_RADIUS = 16.0D;

    private Presets() {
    }

    public static void register() {
        AiApi.registerSensor(new EntitySensor(SENSOR_RADIUS));

        AiApi.registerGoal(new FollowOwner());
        AiApi.registerGoal(new AttackHostile());
        AiApi.registerGoal(new Retreat());
        AiApi.registerGoal(new Patrol());

        // Fallbacks, replaced by the shipped JSON on the first data pack reload. Keeping them
        // in code means a broken data pack can never leave the mod without a working preset.
        AiApi.registerPreset(guard());
        AiApi.registerPreset(companion());
        AiApi.registerPreset(sentry());
        AiApi.registerPreset(blank());

        PresetLoader.register();
    }

    /** Balanced bodyguard: fights, retreats when hurt, follows the owner, light patrol. */
    public static AiPreset guard() {
        return new AiPreset(AICoreLib.id("guard"), "Guard",
                List.of(
                        weight(AttackHostile.ID, 1.0D),
                        weight(Retreat.ID, 1.0D),
                        weight(FollowOwner.ID, 0.8D),
                        weight(Patrol.ID, 0.3D)),
                16.0D, 1.0D, false, 10);
    }

    /** Non-combat pet: sticks close, only fights back, never targets players. */
    public static AiPreset companion() {
        return new AiPreset(AICoreLib.id("companion"), "Companion",
                List.of(
                        weight(FollowOwner.ID, 1.0D),
                        weight(Retreat.ID, 1.0D),
                        weight(AttackHostile.ID, 0.55D),
                        weight(Patrol.ID, 0.2D)),
                12.0D, 1.15D, false, 10);
    }

    /** Stationary guard: holds its home point instead of following anyone. */
    public static AiPreset sentry() {
        return new AiPreset(AICoreLib.id("sentry"), "Sentry",
                List.of(
                        weight(AttackHostile.ID, 1.0D),
                        weight(Retreat.ID, 0.6D),
                        weight(Patrol.ID, 0.5D)),
                20.0D, 0.9D, false, 10);
    }

    /** Does nothing; used to keep a body around with its AI stashed. */
    public static AiPreset blank() {
        return new AiPreset(AICoreLib.id("blank"), "Blank",
                List.of(weight(Patrol.ID, 0.2D)),
                8.0D, 0.8D, false, 20);
    }

    private static AiPreset.GoalWeight weight(net.minecraft.resources.ResourceLocation goal, double value) {
        return new AiPreset.GoalWeight(goal, value);
    }
}
