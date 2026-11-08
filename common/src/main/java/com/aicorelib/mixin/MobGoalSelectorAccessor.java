package com.aicorelib.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mojang mappings keep {@code Mob.goalSelector} protected and expose no getter, so every
 * loader needs a way in. A mixin accessor is used instead of an access widener because
 * NeoForge has no access-widener support at runtime (only access transformers), while the
 * mixin refmap is remapped correctly for Fabric and NeoForge alike.
 */
@Mixin(Mob.class)
public interface MobGoalSelectorAccessor {
    @Accessor("goalSelector")
    GoalSelector aicorelib$goalSelector();
}
