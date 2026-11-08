package com.aicorelib.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Small movement/attack helpers every goal can use.
 *
 * <p>Kept here (rather than inside the goals) so addon presets do not have to
 * re-discover the navigation API.</p>
 */
public final class Actions {
    private Actions() {
    }

    public static void moveTo(Mob mob, Vec3 target, double speed) {
        mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    public static void moveTo(Mob mob, Entity target, double speed) {
        mob.getNavigation().moveTo(target, speed);
    }

    public static void lookAt(Mob mob, Entity target) {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    public static void stop(Mob mob) {
        mob.getNavigation().stop();
    }

    /**
     * Runs one attack swing.
     *
     * <p>Returns {@code false} for mobs without an attack damage attribute (pigs, cows,
     * most passive animals) — {@code doHurtTarget} throws on those, and a library must
     * not take the server down because someone injected a cow.</p>
     *
     * @return whether the hit landed
     */
    public static boolean attack(Mob mob, LivingEntity target) {
        if (target == null || !target.isAlive() || target == mob) {
            return false;
        }
        if (mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == null) {
            return false;
        }
        mob.lookAt(target, 30.0F, 30.0F);
        return mob.doHurtTarget(target);
    }

    /** Whether this mob is physically able to fight at all. */
    public static boolean canFight(Mob mob) {
        return mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null;
    }

    public static boolean isMoving(Mob mob) {
        return !mob.getNavigation().isDone();
    }

    /** Broadcasts a line of chat to players within earshot. */
    public static void say(Mob mob, String text) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Component message = Component.literal("<" + mob.getName().getString() + "> " + text)
                .withStyle(ChatFormatting.GRAY);
        level.getPlayers(p -> p.distanceToSqr(mob) <= 32 * 32)
                .forEach(p -> p.sendSystemMessage(message));
    }
}
