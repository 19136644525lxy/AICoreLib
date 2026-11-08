package com.aicorelib.preset;

import com.aicorelib.AICoreLib;
import com.aicorelib.api.Actions;
import com.aicorelib.api.AgentContext;
import com.aicorelib.api.AiAction;
import com.aicorelib.api.AiApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The five verbs the remote brain is allowed to use.
 *
 * <p>Deliberately small. A model with forty verbs picks the wrong one; a model with five
 * combines them ("walk to the zombie, hit it, back off") well enough. Addons can register
 * their own — {@link AiApi#registerAction(AiAction)} — and those show up in the tool list
 * automatically.</p>
 */
public final class AiActions {
    public static final ResourceLocation MOVE_TO = AICoreLib.id("move_to");
    public static final ResourceLocation FLEE = AICoreLib.id("flee");
    public static final ResourceLocation ATTACK = AICoreLib.id("attack");
    public static final ResourceLocation LOOK_AT = AICoreLib.id("look_at");
    public static final ResourceLocation SAY = AICoreLib.id("say");

    private static final double ATTACK_REACH = 3.5D;

    private AiActions() {
    }

    public static void register() {
        AiApi.registerAction(new MoveTo());
        AiApi.registerAction(new Flee());
        AiApi.registerAction(new Attack());
        AiApi.registerAction(new LookAt());
        AiApi.registerAction(new Say());
    }

    private abstract static class Base implements AiAction {
        private final ResourceLocation id;
        private final String description;

        Base(ResourceLocation id, String description) {
            this.id = id;
            this.description = description;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public String description() {
            return description;
        }
    }

    private static final class MoveTo extends Base {
        MoveTo() {
            super(MOVE_TO, "Walk to an entity you can see in the situation, or to a point near you.");
        }

        @Override
        public String parametersSchema() {
            return "{\"type\":\"object\",\"properties\":{"
                    + "\"target\":{\"type\":\"string\",\"description\":\"entity handle such as e1\"},"
                    + "\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"},\"z\":{\"type\":\"number\"}"
                    + "},\"additionalProperties\":false}";
        }

        @Override
        public void run(AgentContext ctx, CompoundTag args) {
            Mob mob = ctx.mob();
            Entity target = ctx.entityByRef(args.getString("target"));
            if (target != null) {
                Actions.moveTo(mob, target, ctx.speed());
                return;
            }
            if (args.contains("x") && args.contains("y") && args.contains("z")) {
                Actions.moveTo(mob, new Vec3(args.getDouble("x"), args.getDouble("y"), args.getDouble("z")), ctx.speed());
            }
        }
    }

    private static final class Flee extends Base {
        Flee() {
            super(FLEE, "Run away from a threatening entity, putting distance between you and it.");
        }

        @Override
        public String parametersSchema() {
            return "{\"type\":\"object\",\"properties\":{"
                    + "\"target\":{\"type\":\"string\",\"description\":\"the thing to run from\"}"
                    + "},\"required\":[\"target\"],\"additionalProperties\":false}";
        }

        @Override
        public void run(AgentContext ctx, CompoundTag args) {
            Mob mob = ctx.mob();
            Entity threat = ctx.entityByRef(args.getString("target"));
            if (threat == null) {
                return;
            }
            Vec3 away = mob.position().subtract(threat.position());
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Actions.moveTo(mob, mob.position().add(away.normalize().scale(12.0D)), ctx.speed() * 1.3D);
        }
    }

    private static final class Attack extends Base {
        Attack() {
            super(ATTACK, "Hit a nearby entity with your body. Only works within reach and only for mobs that can deal damage.");
        }

        @Override
        public String parametersSchema() {
            return "{\"type\":\"object\",\"properties\":{"
                    + "\"target\":{\"type\":\"string\",\"description\":\"the entity to hit\"}"
                    + "},\"required\":[\"target\"],\"additionalProperties\":false}";
        }

        @Override
        public void run(AgentContext ctx, CompoundTag args) {
            Mob mob = ctx.mob();
            if (!Actions.canFight(mob)) {
                return;
            }
            Entity entity = ctx.entityByRef(args.getString("target"));
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || target == mob) {
                return;
            }
            if (mob.distanceToSqr(target) > ATTACK_REACH * ATTACK_REACH) {
                // Out of reach: at least close the distance instead of swinging at air.
                Actions.moveTo(mob, target, ctx.speed());
                return;
            }
            Actions.attack(mob, target);
        }
    }

    private static final class LookAt extends Base {
        LookAt() {
            super(LOOK_AT, "Turn your head toward an entity without moving.");
        }

        @Override
        public String parametersSchema() {
            return "{\"type\":\"object\",\"properties\":{"
                    + "\"target\":{\"type\":\"string\"}"
                    + "},\"required\":[\"target\"],\"additionalProperties\":false}";
        }

        @Override
        public void run(AgentContext ctx, CompoundTag args) {
            Entity target = ctx.entityByRef(args.getString("target"));
            if (target != null) {
                Actions.lookAt(ctx.mob(), target);
            }
        }
    }

    private static final class Say extends Base {
        Say() {
            super(SAY, "Say one short line out loud. Chat only, no game effect. Use your owner's language.");
        }

        @Override
        public String parametersSchema() {
            return "{\"type\":\"object\",\"properties\":{"
                    + "\"text\":{\"type\":\"string\",\"description\":\"at most 80 characters\"}"
                    + "},\"required\":[\"text\"],\"additionalProperties\":false}";
        }

        @Override
        public void run(AgentContext ctx, CompoundTag args) {
            String text = args.getString("text");
            if (text != null && !text.isBlank()) {
                Actions.say(ctx.mob(), text.length() > 80 ? text.substring(0, 80) : text);
            }
        }
    }
}
