package com.aicorelib.llm;

import com.aicorelib.AICoreLib;
import com.aicorelib.api.AgentContext;
import com.aicorelib.api.AiAction;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.AiPreset;
import com.aicorelib.api.PromptContributor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

/**
 * Builds the two halves of a prompt: the standing instructions and the situation snapshot.
 *
 * <p>The split matters for cost. The system half is stable and cacheable by the provider; the
 * situation half is short and changes every few seconds.</p>
 */
public final class Prompts {
    private static final int MAX_ENTITIES = 8;
    private static final double SCAN_RADIUS = 16.0D;

    private Prompts() {
    }

    /** Tool names must match {@code ^[a-zA-Z0-9_-]+$}, so namespaces are flattened. */
    public static String toolName(ResourceLocation id) {
        String raw = id.getNamespace() + "_" + id.getPath();
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    @Nullable
    public static ResourceLocation actionFor(String toolName) {
        for (AiAction action : AiApi.actions()) {
            if (toolName(action.id()).equals(toolName)) {
                return action.id();
            }
        }
        return null;
    }

    public static JsonArray tools() {
        JsonArray tools = new JsonArray();
        for (AiAction action : AiApi.actions()) {
            JsonObject function = new JsonObject();
            function.addProperty("name", toolName(action.id()));
            function.addProperty("description", action.description());
            function.add("parameters", com.google.gson.JsonParser.parseString(action.parametersSchema()));

            JsonObject tool = new JsonObject();
            tool.addProperty("type", "function");
            tool.add("function", function);
            tools.add(tool);
        }
        return tools;
    }

    public static String system(AgentContext ctx) {
        AiPreset preset = ctx.preset();
        List<String> lines = new ArrayList<>();
        lines.add("You are the mind of a Minecraft mob driven by the AICoreLib mod. "
                + "You are not a chatbot: you control a body in a survival world.");
        lines.add("Answer with exactly one tool call when you want to act. "
                + "Answer with plain text only when you have nothing to do, and keep it under 20 words.");
        lines.add("Rules:");
        lines.add("- You only know what the SITUATION block tells you. Never invent entities, items or places.");
        lines.add("- You cannot break or place blocks, open containers, craft, or fly.");
        lines.add("- You cannot jump on command; the body's own reflexes handle water, cliffs and lava.");
        lines.add("- Prefer doing nothing when nothing threatens you or the one you follow.");
        lines.add("- Stay within about 32 blocks of your owner unless they are in danger.");
        if (preset != null) {
            lines.add("Preset: " + preset.displayName() + " (" + preset.id() + ")");
        }
        lines.add("Available actions: " + String.join(", ", AiApi.actions().stream()
                .map(a -> toolName(a.id())).toList()));

        List<PromptContributor> contributors = new ArrayList<>(AiApi.promptContributors());
        contributors.sort(Comparator.comparingInt(PromptContributor::priority));
        for (PromptContributor contributor : contributors) {
            try {
                contributor.contribute(ctx, lines);
            } catch (RuntimeException e) {
                AICoreLib.LOGGER.warn("Prompt contributor {} failed", contributor.getClass().getName(), e);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Renders the current situation and binds the {@code e1}, {@code e2}… handles into
     * {@code ctx} so the actions can resolve them when the answer comes back.
     */
    public static String situation(AgentContext ctx) {
        Mob mob = ctx.mob();
        StringBuilder sb = new StringBuilder();

        sb.append("SITUATION\n");
        sb.append("you: ").append(mob.getType().toShortString())
                .append(", health ").append(fmt(mob.getHealth())).append("/").append(fmt(mob.getMaxHealth()))
                .append(", position ").append(fmt(mob.getX())).append(" ").append(fmt(mob.getY())).append(" ").append(fmt(mob.getZ()))
                .append('\n');
        sb.append("can_attack: ").append(com.aicorelib.api.Actions.canFight(mob) ? "yes" : "no").append('\n');

        Player owner = owner(ctx);
        if (owner != null) {
            sb.append("owner: ").append(owner.getGameProfile().getName())
                    .append(" at ").append(fmt(owner.distanceTo(mob))).append("m")
                    .append(", health ").append(fmt(owner.getHealth())).append('\n');
        } else if (ctx.ownerId() != null) {
            sb.append("owner: not nearby\n");
        } else {
            sb.append("owner: none\n");
        }

        Map<String, UUID> refs = new HashMap<>();
        List<Entity> nearby = new ArrayList<>();
        for (Entity entity : mob.level().getEntities(mob, mob.getBoundingBox().inflate(SCAN_RADIUS))) {
            if (entity instanceof LivingEntity && entity.isAlive() && entity != mob) {
                nearby.add(entity);
            }
        }
        nearby.sort(Comparator.comparingDouble(mob::distanceToSqr));

        sb.append("nearby:\n");
        int index = 0;
        for (Entity entity : nearby) {
            if (index >= MAX_ENTITIES) {
                break;
            }
            index++;
            String handle = "e" + index;
            refs.put(handle, entity.getUUID());
            sb.append("  ").append(handle).append(' ').append(entity.getType().toShortString());
            if (entity instanceof LivingEntity living) {
                sb.append(" health ").append(fmt(living.getHealth()));
            }
            if (entity instanceof Player player) {
                sb.append(" player ").append(player.getGameProfile().getName());
            }
            if (entity instanceof Mob other && other.getTarget() == mob) {
                sb.append(" ANGRY_AT_YOU");
            }
            sb.append(" dist ").append(fmt(mob.distanceTo(entity))).append('\n');
        }
        if (index == 0) {
            sb.append("  (nothing within ").append((int) SCAN_RADIUS).append("m)\n");
        }
        ctx.setRefs(refs);

        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            sb.append("current_target: ").append(mob.getTarget().getType().toShortString()).append('\n');
        }
        if (mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100) {
            sb.append("recently_hurt_by: ").append(mob.getLastHurtByMob().getType().toShortString()).append('\n');
        }
        return sb.toString();
    }

    @Nullable
    private static Player owner(AgentContext ctx) {
        if (ctx.ownerId() == null || ctx.server() == null) {
            return null;
        }
        return ctx.server().getPlayerList().getPlayer(ctx.ownerId());
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
