package com.aicorelib.core;

import com.aicorelib.AICoreLib;
import com.aicorelib.agent.AgentHandle;
import com.aicorelib.agent.AgentRecord;
import com.aicorelib.agent.AgentRuntime;
import com.aicorelib.agent.AgentSavedData;
import com.aicorelib.agent.AgentService;
import com.aicorelib.agent.InjectionResult;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.AiPreset;
import com.aicorelib.llm.LlmBudget;
import com.aicorelib.llm.LlmConfig;
import com.aicorelib.llm.LlmProviders;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin/debug commands.
 *
 * <pre>
 * /aicorelib list                        - list every injected agent
 * /aicorelib inject &lt;targets&gt; [preset]   - attach the AI to mobs without the item
 * /aicorelib remove &lt;targets&gt;            - release mobs from the AI
 * /aicorelib clear                       - remove every injected agent
 * /aicorelib info &lt;targets&gt;              - one-screen summary of an agent
 * /aicorelib pause|resume &lt;targets&gt;      - freeze the AI, keep the record and memory
 * /aicorelib presets                     - list presets (code + data pack)
 * /aicorelib set &lt;targets&gt; &lt;preset&gt;      - swap the preset of existing agents
 * /aicorelib debug &lt;targets&gt;             - what the brains currently see
 * /aicorelib llm status|reload           - model configuration and budget
 * /aicorelib ask &lt;targets&gt; &lt;text&gt;        - force one natural-language order
 * /aicorelib reload                      - re-read config/aicorelib.json and the LLM config
 * </pre>
 */
public final class ModCommands {
    private static final int ADMIN_PERMISSION = 2;

    private ModCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                dispatcher.register(Commands.literal("aicorelib")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("list").executes(ModCommands::list))
                        .then(Commands.literal("inject")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> inject(ctx, AgentService.defaultPreset()))
                                        .then(Commands.argument("preset", ResourceLocationArgument.id())
                                                .suggests(ModCommands::presetSuggestions)
                                                .executes(ctx -> inject(ctx,
                                                        ResourceLocationArgument.getId(ctx, "preset").toString())))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> remove(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets")))))
                        .then(Commands.literal("clear").executes(ModCommands::clear))
                        .then(Commands.literal("info")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> info(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets")))))
                        .then(Commands.literal("pause")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> setPaused(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets"), true))))
                        .then(Commands.literal("resume")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> setPaused(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets"), false))))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> debug(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets")))))
                        .then(Commands.literal("presets").executes(ModCommands::presets))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("preset", ResourceLocationArgument.id())
                                                .suggests(ModCommands::presetSuggestions)
                                                .executes(ctx -> setPreset(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "preset").toString())))))
                        .then(Commands.literal("llm")
                                .then(Commands.literal("status").executes(ctx -> llmStatus(ctx.getSource())))
                                .then(Commands.literal("reload").executes(ctx -> llmReload(ctx.getSource()))))
                        .then(Commands.literal("ask")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> ask(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "text"))))))
                        .then(Commands.literal("reload").executes(ctx -> reloadAll(ctx.getSource())))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
        Map<UUID, AgentRecord> agents = data.agents();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[AICoreLib] injected agents: " + agents.size()), false);
        agents.forEach((uuid, record) -> ctx.getSource().sendSuccess(
                () -> Component.literal(" - " + uuid + " | " + record.entityType()
                        + " | preset=" + record.presetId()
                        + (record.paused() ? " | paused" : "")), false));
        return agents.size();
    }

    private static int inject(CommandContext<CommandSourceStack> ctx, String preset) {
        CommandSourceStack source = ctx.getSource();
        Entity executor = source.getEntity();
        UUID owner = executor == null ? null : executor.getUUID();
        Collection<? extends Entity> targets;
        try {
            targets = EntityArgument.getEntities(ctx, "targets");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("[AICoreLib] " + e.getMessage()));
            return 0;
        }

        Map<InjectionResult, Integer> counts = new EnumMap<>(InjectionResult.class);
        for (Entity target : targets) {
            if (target instanceof Mob mob) {
                counts.merge(AgentService.tryInject(mob, owner, preset), 1, Integer::sum);
            }
        }

        int injected = counts.getOrDefault(InjectionResult.OK, 0);
        StringBuilder line = new StringBuilder("[AICoreLib] injected ").append(injected)
                .append(" mob(s) as ").append(preset);
        counts.forEach((result, count) -> {
            if (result != InjectionResult.OK) {
                line.append("; skipped ").append(count).append(" (").append(result.name().toLowerCase()).append(')');
            }
        });
        String text = line.toString();
        source.sendSuccess(() -> Component.literal(text), true);
        return injected;
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

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
        int removed = data.clear();
        AgentRuntime.untrackAll();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[AICoreLib] cleared " + removed + " agent(s)"), true);
        return removed;
    }

    /** One-screen summary: identity on the first line, state on the second. */
    private static int info(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity target : targets) {
            if (!(target instanceof Mob mob)) {
                continue;
            }
            AgentHandle handle = AgentRuntime.handle(mob);
            AgentRecord record = handle == null ? null : handle.record();
            if (record == null) {
                source.sendSuccess(() -> Component.literal("[AICoreLib] " + mob.getName().getString()
                        + " is not an agent"), false);
                continue;
            }
            String ownerName = record.owner() == null
                    ? "none"
                    : String.valueOf(source.getServer().getPlayerList().getPlayer(record.owner()) == null
                            ? record.owner().toString()
                            : source.getServer().getPlayerList().getPlayer(record.owner()).getName().getString());
            String identity = String.format(
                    "[AICoreLib] %s | preset=%s | owner=%s | hops=%d/%d | affection=%d | paused=%s",
                    mob.getName().getString(), record.presetId(), ownerName,
                    record.hops(), AgentRecord.MAX_HOPS, record.affection(), record.paused());
            source.sendSuccess(() -> Component.literal(identity), false);

            if (handle != null) {
                var active = handle.brain().activeGoal();
                String state = String.format(
                        "[AICoreLib] health=%.1f/%.1f | goal=%s | remote=%s | memory=%s",
                        mob.getHealth(), mob.getMaxHealth(),
                        active == null ? "none" : active.id().toString(),
                        handle.remoteAction() != null ? "active(" + handle.remoteAction() + ")" : "idle",
                        record.memorySummary().isEmpty() ? "(empty)" : record.memorySummary());
                source.sendSuccess(() -> Component.literal(state), false);
            }
            count++;
        }
        return count;
    }

    private static int setPaused(CommandSourceStack source, Collection<? extends Entity> targets, boolean paused) {
        int count = 0;
        for (Entity target : targets) {
            if (target instanceof Mob mob && AgentService.setPaused(mob, paused)) {
                count++;
            }
        }
        int total = count;
        source.sendSuccess(() -> Component.literal("[AICoreLib] " + (paused ? "paused " : "resumed ")
                + total + " agent(s)"), true);
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
            String remote = handle.brain().suspended() ? "suspended" : "idle";
            if (handle.remoteAction() != null) {
                remote = "active(" + handle.remoteAction() + ")";
            }
            String line = String.format(
                    "[AICoreLib] %s | preset=%s | goal=%s | control=%s | remote=%s | hostile=%s | owner=%s | health=%.1f/%.1f",
                    mob.getName().getString(),
                    handle.record().presetId(),
                    active == null ? "none" : active.id().toString(),
                    handle.brain().wantsControl(),
                    remote,
                    hostile,
                    owner,
                    mob.getHealth(),
                    mob.getMaxHealth());
            source.sendSuccess(() -> Component.literal(line), false);
            count++;
        }
        return count;
    }

    private static int presets(CommandContext<CommandSourceStack> ctx) {
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

    private static int llmReload(CommandSourceStack source) {
        LlmConfig config = LlmConfig.load();
        LlmBudget.reset();
        source.sendSuccess(() -> Component.literal(
                "[AICoreLib] LLM config reloaded: enabled=" + config.enabled()
                        + " model=" + config.model()
                        + " key=" + config.maskedKey()), false);
        return 1;
    }

    /** Re-reads both config files; data pack presets still need a vanilla {@code /reload}. */
    private static int reloadAll(CommandSourceStack source) {
        AICoreSettings settings = AICoreSettings.load();
        LlmConfig config = LlmConfig.load();
        LlmBudget.reset();
        source.sendSuccess(() -> Component.literal(String.format(
                "[AICoreLib] reloaded: max_agents_total=%d, max_agents_per_owner=%d, default_preset=%s, llm=%s",
                settings.maxAgentsTotal(), settings.maxAgentsPerOwner(), settings.defaultPreset(),
                config.enabled())), false);
        source.sendSuccess(() -> Component.literal(
                "[AICoreLib] presets live in data packs; run /reload to pick up edited JSON"), false);
        return 1;
    }

    /** Pushes one natural-language order to every targeted agent, bypassing the trigger heuristics. */
    private static int ask(CommandSourceStack source, Collection<? extends Entity> targets, String instruction) {
        int count = 0;
        AICoreLib.LOGGER.info("ask: [{}] ({} chars)", instruction, instruction.length());
        for (Entity target : targets) {
            if (!(target instanceof Mob mob)) {
                continue;
            }
            AgentHandle handle = AgentRuntime.handle(mob);
            if (handle == null) {
                continue;
            }
            if (handle.remote().ask(instruction, source.getPlayer())) {
                count++;
            }
        }
        int accepted = count;
        if (accepted == 0) {
            source.sendFailure(Component.literal(
                    "[AICoreLib] no request sent (LLM disabled, no key, agent paused, or an answer is already in flight)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[AICoreLib] asked " + accepted + " agent(s): " + instruction), false);
        return count;
    }

    private static CompletableFuture<Suggestions> presetSuggestions(CommandContext<CommandSourceStack> ctx,
                                                                   SuggestionsBuilder builder) {
        for (AiPreset preset : AiApi.presets()) {
            builder.suggest(preset.id().toString());
        }
        return builder.buildFuture();
    }
}
