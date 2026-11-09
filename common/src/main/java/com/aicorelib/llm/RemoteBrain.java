package com.aicorelib.llm;

import com.aicorelib.AICoreLib;
import com.aicorelib.agent.AgentHandle;
import com.aicorelib.api.AiAction;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.AiDirective;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The event-driven half of the brain.
 *
 * <p>It does not think every tick. It asks a model when something worth noticing happened
 * (a new threat, a hit, the owner arriving) or on a slow heartbeat, then applies the answer
 * for a few seconds before handing control back to {@link com.aicorelib.brain.LocalBrain}.</p>
 *
 * <p>Everything that touches the world happens on the server thread: the HTTP callback may
 * only deposit a value into volatile fields.</p>
 */
public final class RemoteBrain {
    private static final int ACTION_REPEAT_TICKS = 10;

    private final AgentHandle handle;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    @Nullable
    private volatile ResourceLocation pendingAction;
    @Nullable
    private volatile CompoundTag pendingArgs;

    @Nullable
    private AiDirective directive;
    private long lastActionTick;

    private long lastRequestTick = Long.MIN_VALUE;
    private String lastSituation = "";
    private String lastError = "";
    /** Set while a request started by {@code /aicorelib ask} is in flight, so silence gets reported. */
    private volatile boolean explicit;
    /** Who typed {@code /aicorelib ask}, so a text-only answer can be echoed back to them. */
    @Nullable
    private volatile java.util.UUID requesterId;
    /** A reply written by the HTTP thread, delivered on the server thread by {@link #consumeReply()}. */
    @Nullable
    private volatile String pendingReply;

    public RemoteBrain(AgentHandle handle) {
        this.handle = handle;
    }

    /** Whether the remote brain currently owns the body. */
    public boolean active() {
        return directive != null;
    }

    /** The action currently being executed, for {@code /aicorelib debug}. */
    @Nullable
    public ResourceLocation activeAction() {
        AiDirective current = directive;
        return current == null ? null : current.actionId();
    }

    /** Called once per server tick from {@link AgentHandle#tick(long)}. */
    public void tick(long now) {
        consumePending(now);
        consumeReply();

        if (directive != null) {
            if (directive.expired(now)) {
                directive = null;
                handle.brain().setSuspended(false);
            } else {
                apply(now);
                return;
            }
        }

        LlmConfig config = LlmConfig.get();
        if (!config.isUsable() || inFlight.get()) {
            return;
        }
        long cooldown = Math.max(20L, config.heartbeatTicks() / 4L);
        boolean never = lastRequestTick == Long.MIN_VALUE;
        if (!never && now - lastRequestTick < cooldown) {
            return;
        }

        Mob mob = handle.mob();
        if (config.requirePlayerNearby() && !playerNearby(mob, config.activationRadius())) {
            return;
        }

        String situation = Prompts.situation(handle.context());
        boolean changed = !situation.equals(lastSituation);
        boolean heartbeat = never || now - lastRequestTick >= config.heartbeatTicks();
        if (!changed && !heartbeat) {
            return;
        }
        if (!LlmBudget.allowRequest(mob.getUUID())) {
            return;
        }
        lastSituation = situation;
        lastRequestTick = now;
        send(Prompts.system(handle.context()) + "\n\n" + situation, null);
    }

    /**
     * Forces one request with an extra instruction, used by {@code /aicorelib ask}.
     *
     * @return false when a request is already in flight or the LLM is unusable
     */
    public boolean ask(String instruction) {
        return ask(instruction, null);
    }

    /**
     * Same as {@link #ask(String)}, but a text-only answer (no tool call) is echoed back to
     * {@code requester} instead of vanishing into the log.
     */
    public boolean ask(String instruction, @Nullable ServerPlayer requester) {
        if (inFlight.get() || !LlmConfig.get().isUsable()) {
            return false;
        }
        String situation = Prompts.situation(handle.context());
        lastSituation = situation;
        lastRequestTick = handle.context().tick();
        explicit = true;
        requesterId = requester == null ? null : requester.getUUID();
        send(Prompts.system(handle.context()) + "\n\n" + situation, instruction);
        return true;
    }

    @Nullable
    public String lastError() {
        return lastError.isEmpty() ? null : lastError;
    }

    private void send(String system, @Nullable String instruction) {
        inFlight.set(true);
        LlmConfig config = LlmConfig.get();
        LlmRequest request = new LlmRequest(
                config.model(),
                instruction == null
                        ? List.of(LlmMessage.system(system), LlmMessage.user("Decide what to do now."))
                        : List.of(LlmMessage.system(system), LlmMessage.user(instruction)),
                Prompts.tools(),
                config.temperature(),
                config.maxTokens(),
                config.timeoutSeconds());

        LlmBudget.noteRequest();
        CompletableFuture<LlmResponse> future;
        try {
            future = LlmProviders.get().complete(request);
        } catch (RuntimeException e) {
            inFlight.set(false);
            LlmBudget.recordFailure(handle.mob().getUUID(), e.toString());
            return;
        }
        future.whenComplete((response, error) -> {
            inFlight.set(false);
            if (error != null) {
                lastError = String.valueOf(error);
                LlmBudget.recordFailure(handle.mob().getUUID(), lastError);
                return;
            }
            if (response == null || !response.ok()) {
                lastError = response == null ? "no response" : String.valueOf(response.error());
                LlmBudget.recordFailure(handle.mob().getUUID(), lastError);
                return;
            }
            LlmBudget.recordSuccess(handle.mob().getUUID(), response.totalTokens());
            lastError = "";
            boolean wasExplicit = explicit;
            explicit = false;
            LlmMessage.ToolCall call = response.firstToolCall();
            if (call == null) {
                if (wasExplicit) {
                    // Reasoning models burn the whole budget on thinking and answer with nothing;
                    // say so instead of leaving the admin wondering why the mob ignored them.
                    String text = response.content() == null ? "" : response.content().strip();
                    AICoreLib.LOGGER.info("LLM returned no action ({} completion tokens): {}",
                            response.completionTokens(),
                            text.isEmpty() ? "<empty>" : text.substring(0, Math.min(160, text.length())));
                    if (!text.isEmpty()) {
                        pendingReply = text.substring(0, Math.min(300, text.length()));
                    }
                }
                return;
            }
            ResourceLocation actionId = Prompts.actionFor(call.name());
            if (actionId == null) {
                lastError = "unknown tool: " + call.name();
                AICoreLib.LOGGER.warn("LLM asked for unknown tool '{}'", call.name());
                return;
            }
            pendingAction = actionId;
            pendingArgs = toTag(call.arguments());
        });
    }

    /**
     * Delivers a text-only answer (the model talked instead of acting) to whoever typed
     * {@code /aicorelib ask}. Runs on the server thread like everything else here.
     */
    private void consumeReply() {
        String reply = pendingReply;
        if (reply == null) {
            return;
        }
        pendingReply = null;
        java.util.UUID target = requesterId;
        requesterId = null;
        if (target == null) {
            return;
        }
        ServerPlayer player = handle.mob().level().getServer().getPlayerList().getPlayer(target);
        if (player != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[AICoreLib] " + handle.mob().getName().getString() + ": " + reply));
        }
    }

    /** Moves a value from the HTTP thread onto the server thread. */
    private void consumePending(long now) {
        ResourceLocation actionId = pendingAction;
        if (actionId == null) {
            return;
        }
        CompoundTag args = pendingArgs;
        pendingAction = null;
        pendingArgs = null;
        if (AiApi.action(actionId) == null) {
            return;
        }
        int ttl = Math.max(20, LlmConfig.get().directiveTtlTicks());
        directive = new AiDirective(actionId, args, now, now + ttl);
        lastActionTick = Long.MIN_VALUE;
        handle.brain().setSuspended(true);
    }

    private void apply(long now) {
        AiDirective current = directive;
        if (current == null || now - lastActionTick < ACTION_REPEAT_TICKS && lastActionTick != Long.MIN_VALUE) {
            return;
        }
        lastActionTick = now;
        AiAction action = AiApi.action(current.actionId());
        if (action == null) {
            directive = null;
            handle.brain().setSuspended(false);
            return;
        }
        try {
            if (action.canRun(handle.context())) {
                action.run(handle.context(), current.args());
            } else {
                // The model picked something this body cannot do (a pig asked to attack, say).
                // Hand control back at once instead of spinning until the directive expires.
                AICoreLib.LOGGER.info("LLM chose {} but {} cannot run it; handing back to the rule brain",
                        current.actionId(), handle.mob().getUUID());
                directive = null;
                handle.brain().setSuspended(false);
            }
        } catch (RuntimeException e) {
            // A third-party action throwing must not kill the agent; drop the directive.
            AICoreLib.LOGGER.warn("Action {} failed for {}", current.actionId(), handle.mob().getUUID(), e);
            directive = null;
            handle.brain().setSuspended(false);
        }
    }

    private static boolean playerNearby(Mob mob, int radius) {
        double r = radius * radius;
        for (ServerPlayer player : mob.level().getServer().getPlayerList().getPlayers()) {
            if (player.level() == mob.level() && player.distanceToSqr(mob) <= r) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag toTag(JsonObject json) {
        CompoundTag tag = new CompoundTag();
        for (var entry : json.entrySet()) {
            var value = entry.getValue();
            if (value.isJsonPrimitive()) {
                var primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    tag.putDouble(entry.getKey(), primitive.getAsDouble());
                } else if (primitive.isBoolean()) {
                    tag.putBoolean(entry.getKey(), primitive.getAsBoolean());
                } else {
                    tag.putString(entry.getKey(), primitive.getAsString());
                }
            } else if (value.isJsonArray()) {
                ListTag list = new ListTag();
                for (var element : value.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        list.add(net.minecraft.nbt.StringTag.valueOf(element.getAsString()));
                    }
                }
                tag.put(entry.getKey(), list);
            }
        }
        return tag;
    }
}
