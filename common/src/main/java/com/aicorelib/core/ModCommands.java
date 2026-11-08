package com.aicorelib.core;

import com.aicorelib.agent.AgentHandle;
import com.aicorelib.agent.AgentRecord;
import com.aicorelib.agent.AgentRuntime;
import com.aicorelib.agent.AgentSavedData;
import com.aicorelib.agent.AgentService;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.AiPreset;
import com.aicorelib.llm.LlmBudget;
import com.aicorelib.llm.LlmConfig;
import com.aicorelib.llm.LlmProviders;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Admin/debug commands.
 *
 * <pre>
 * /aicorelib list                      - list every injected agent
 * /aicorelib inject &lt;targets&gt; [preset] - attach the AI to mobs without the item
 * /aicorelib remove &lt;targets&gt;          - release mobs from the AI
 * /aicorelib clear                     - remove every injected agent
 * </pre>
 */
public final class ModCommands {
    private ModCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                dispatcher.register(Commands.literal("aicorelib")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list").executes(ctx -> {
                            AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
                            Map<UUID, AgentRecord> agents = data.agents();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AICoreLib] injected agents: " + agents.size()), false);
                            agents.forEach((uuid, record) -> ctx.getSource().sendSuccess(
                                    () -> Component.literal(" - " + uuid + " | " + record.entityType()
                                            + " | preset=" + record.presetId()), false));
                            return agents.size();
                        }))
                        .then(Commands.literal("inject")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> inject(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets"),
                                                AgentRecord.DEFAULT_PRESET))
                                        .then(Commands.argument("preset", ResourceLocationArgument.id())
                                                .executes(ctx -> inject(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "preset").toString())))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> remove(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets")))))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> debug(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets")))))
                        .then(Commands.literal("clear").executes(ctx -> {
                            AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
                            int removed = data.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AICoreLib] cleared " + removed + " agent(s)"), true);
                            return removed;
                        }))
                        .then(Commands.literal("llm")
                                .then(Commands.literal("status").executes(ctx -> llmStatus(ctx.getSource())))
                                .then(Commands.literal("reload").executes(ctx -> {
                                    LlmConfig config = LlmConfig.load();
                                    LlmBudget.reset();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[AICoreLib] LLM config reloaded: enabled=" + config.enabled()
                                                    + " model=" + config.model()
                                                    + " key=" + config.maskedKey()), false);
                                    return 1;
                                })))
                        .then(Commands.literal("ask")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> ask(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "text"))))))
                        .then(Commands.literal("presets").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AICoreLib] " + AiApi.presets().size() + " preset(s):"), false);
                            for (AiPreset preset : AiApi.presets()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(" - " + preset.id()
                                        + " | " + preset.displayName()
                                        + " | goals=" + preset.goals().size()
                                        + " | speed=" + preset.speed()
                                        + " | interval=" + preset.decisionIntervalTicks()), false);
                            }
                            return AiApi.presets().size();
                        }))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("preset", ResourceLocationArgument.id())
                                                .executes(ctx -> setPreset(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "preset").toString())))))));
    }

    private static int inject(CommandSourceStack source, Collection<? extends Entity> targets, String preset)
            throws CommandSyntaxException {
        Entity executor = source.getEntity();
        UUID owner = executor == null ? null : executor.getUUID();
        int count = 0;
        for (Entity target : targets) {
            if (target instanceof Mob mob && AgentService.inject(mob, owner, preset)) {
                count++;
            }
        }
        int injected = count;
        source.sendSuccess(() -> Component.literal("[AICoreLib] injected " + injected + " mob(s) as " + preset), true);
        return count;
    }

    /** Dumps what the brain currently sees; used while tuning the utility AI. */
    private static int debug(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity target : targets) {
            if (!(target instanceof Mob mob)) {
                continue;
            }
            AgentHandle handle = AgentRuntime.handle(mob);
            if (handle == null) {
                source.sendSuccess(() -> Component.literal("[AICoreLib] " + mob.getName().getString()
                        + " is not tracked"), false);
                continue;
            }
            var perception = handle.context().perception();
            var active = handle.brain().activeGoal();
            String hostile = perception.nearestHostile() == null
                    ? "none"
                    : perception.nearestHostile().getName().getString()
                            + "@" + String.format("%.1f", perception.nearestHostileDistance());
            String owner = perception.owner() == null
                    ? "none"
                    : perception.owner().getName().getString()
                            + "@" + String.format("%.1f", perception.ownerDistance());
            String line = String.format(
                    "[AICoreLib] %s | preset=%s | goal=%s | control=%s | remote=%s | hostile=%s | owner=%s | health=%.1f/%.1f",
                    mob.getName().getString(),
                    handle.record().presetId(),
                    active == null ? "none" : active.id().toString(),
                    handle.brain().wantsControl(),
                    handle.remoteActive() ? "active" : (handle.brain().suspended() ? "suspended" : "idle"),
                    hostile,
                    owner,
                    mob.getHealth(),
                    mob.getMaxHealth());
            source.sendSuccess(() -> Component.literal(line), false);
            count++;
        }
        return count;
    }

    /** Swaps the preset of already-injected mobs; handy for iterating on data pack presets. */
    private static int setPreset(CommandSourceStack source, Collection<? extends Entity> targets, String presetId) {
        ResourceLocation parsed = ResourceLocation.tryParse(presetId);
        if (parsed == null || AiApi.preset(parsed) == null) {
            source.sendFailure(Component.literal("[AICoreLib] unknown preset: " + presetId));
            return 0;
        }
        int count = 0;
        for (Entity target : targets) {
            if (target instanceof Mob mob && AgentService.reassign(mob, presetId)) {
                count++;
            }
        }
        int total = count;
        source.sendSuccess(() -> Component.literal(
                "[AICoreLib] applied " + presetId + " to " + total + " agent(s)"), true);
        return count;
    }

    /** Shows the effective LLM configuration without leaking the key. */
    private static int llmStatus(CommandSourceStack source) {
        LlmConfig config = LlmConfig.get();
        String head = String.format(
                "[AICoreLib] llm enabled=%s provider=%s model=%s endpoint=%s key=%s",
                config.enabled(),
                LlmProviders.get().id(),
                config.model(),
                config.endpoint(),
                config.maskedKey());
        source.sendSuccess(() -> Component.literal(head), false);
        source.sendSuccess(() -> Component.literal("[AICoreLib] " + LlmBudget.status()), false);
        int active = 0;
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (entity instanceof Mob mob) {
                AgentHandle handle = AgentRuntime.handle(mob);
                if (handle != null && handle.remoteActive()) {
                    active++;
                }
            }
        }
        int count = active;
        source.sendSuccess(() -> Component.literal("[AICoreLib] agents under remote control: " + count), false);
        return 1;
    }

    /** Pushes one natural-language order to every targeted agent, bypassing the trigger heuristics. */
    private static int ask(CommandSourceStack source, Collection<? extends Entity> targets, String instruction) {
        int count = 0;
        com.aicorelib.AICoreLib.LOGGER.info("ask: [{}] ({} chars)", instruction, instruction.length());
        for (Entity target : targets) {
            if (!(target instanceof Mob mob)) {
                continue;
            }
            AgentHandle handle = AgentRuntime.handle(mob);
            if (handle == null) {
                continue;
            }
            if (handle.remote().ask(instruction)) {
                count++;
            }
        }
        int accepted = count;
        if (accepted == 0) {
            source.sendFailure(Component.literal(
                    "[AICoreLib] no request sent (LLM disabled, no key, or an answer is already in flight)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[AICoreLib] asked " + accepted + " agent(s): " + instruction), false);
        return count;
    }

    private static int remove(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity target : targets) {
            if (target instanceof Mob mob && AgentService.remove(mob)) {
                count++;
            }
        }
        int removed = count;
        source.sendSuccess(() -> Component.literal("[AICoreLib] released " + removed + " mob(s)"), true);
        return count;
    }
}
