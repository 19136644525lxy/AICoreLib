package com.aicorelib.agent;

import com.aicorelib.core.AICoreSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Injection / de-injection entry point shared by the injector item and the commands.
 *
 * <p>All state lives in {@link AgentSavedData}; the mob itself is not modified in M0.
 * Later milestones hook their behaviour layer onto {@link #inject} and {@link #remove}
 * so that every entry point (item, command, core, addon) goes through one place.</p>
 */
public final class AgentService {
    private AgentService() {
    }

    /**
     * Brings {@code mob} under AI control.
     *
     * @return {@code true} if the mob was not already an agent
     */
    public static boolean inject(Mob mob, UUID owner, String presetId) {
        return tryInject(mob, owner, presetId) == InjectionResult.OK;
    }

    /**
     * The checked path used by the item, the commands and {@code /aicorelib inject}.
     *
     * <p>Everything that can refuse an injection refuses here: entity type tags, the per-owner
     * budget and the server-wide budget. Callers turn the result into a chat message.</p>
     */
    public static InjectionResult tryInject(Mob mob, @Nullable UUID owner, String presetId) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return InjectionResult.ALREADY;
        }
        AgentSavedData data = AgentSavedData.get(server);
        UUID id = mob.getUUID();
        if (data.isInjected(id)) {
            return InjectionResult.ALREADY;
        }
        if (!mayHost(mob)) {
            return InjectionResult.DENIED_TYPE;
        }

        AICoreSettings settings = AICoreSettings.get();
        int total = data.agents().size();
        int owned = owner == null ? 0 : data.countOwnedBy(owner);
        if (settings.maxAgentsTotal() > 0 && total >= settings.maxAgentsTotal()) {
            return InjectionResult.LIMIT_TOTAL;
        }
        if (owner != null && settings.maxAgentsPerOwner() > 0 && owned >= settings.maxAgentsPerOwner()) {
            return InjectionResult.LIMIT_OWNER;
        }

        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        AgentRecord record = AgentRecord.create(presetId, owner, entityType,
                server.overworld().getGameTime(), mob.position());
        data.put(id, record);
        AgentRuntime.track(mob, record);
        return InjectionResult.OK;
    }

    /** Whether the entity type may host an AI, judged by the two entity type tags. */
    public static boolean mayHost(Mob mob) {
        var registry = mob.getServer();
        if (registry == null) {
            return false;
        }
        var types = registry.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        var holder = mob.getType().builtInRegistryHolder();
        var denied = types.getTag(AgentTags.NOT_INJECTABLE);
        if (denied.isPresent() && denied.get().contains(holder)) {
            return false;
        }
        var allowed = types.getTag(AgentTags.INJECTABLE);
        return allowed.isEmpty() || allowed.get().size() == 0 || allowed.get().contains(holder);
    }

    /**
     * Brings {@code mob} under AI control with a pre-built record (used by core transfer).
     *
     * @return {@code true} if the mob was not already an agent
     */
    public static boolean inject(Mob mob, AgentRecord record) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return false;
        }
        AgentSavedData data = AgentSavedData.get(server);
        UUID id = mob.getUUID();
        if (data.isInjected(id)) {
            return false;
        }
        data.put(id, record);
        AgentRuntime.track(mob, record);
        return true;
    }

    /**
     * Releases {@code mob} from AI control.
     *
     * @return {@code true} if the mob was an agent
     */
    public static boolean remove(Mob mob) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return false;
        }
        AgentRuntime.untrack(mob);
        return AgentSavedData.get(server).remove(mob.getUUID());
    }

    /**
     * Swaps the preset of an existing agent, keeping owner, home, lineage and memory.
     *
     * @return {@code true} if the mob was an agent
     */
    public static boolean reassign(Mob mob, String presetId) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return false;
        }
        AgentSavedData data = AgentSavedData.get(server);
        UUID id = mob.getUUID();
        AgentRecord existing = data.agents().get(id);
        if (existing == null) {
            return false;
        }
        AgentRecord updated = new AgentRecord(presetId, existing.owner(), existing.entityType(),
                existing.injectedAt(), existing.home(), existing.hops(),
                existing.memorySummary(), existing.affection(), existing.paused());
        data.put(id, updated);
        // The context captures the preset id, so the handle has to be rebuilt.
        AgentRuntime.untrack(mob);
        AgentRuntime.track(mob, updated);
        return true;
    }

    /**
     * Freezes or unfreezes an agent. A paused agent keeps its record (and therefore its memory),
     * but both brains stop and the vanilla behaviour comes back.
     *
     * @return {@code true} if the mob was an agent
     */
    public static boolean setPaused(Mob mob, boolean paused) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return false;
        }
        AgentSavedData data = AgentSavedData.get(server);
        AgentRecord existing = data.agents().get(mob.getUUID());
        if (existing == null || existing.paused() == paused) {
            return existing != null;
        }
        AgentRecord updated = existing.withPaused(paused);
        data.put(mob.getUUID(), updated);
        AgentRuntime.untrack(mob);
        AgentRuntime.track(mob, updated);
        return true;
    }

    /**
     * Item behaviour: toggle between injected and released.
     */
    public static InteractionResult toggle(Player player, Mob mob) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return InteractionResult.PASS;
        }

        if (remove(mob)) {
            player.displayClientMessage(
                    Component.translatable("message.aicorelib.removed").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }

        InjectionResult result = tryInject(mob, player.getUUID(), defaultPreset());
        player.displayClientMessage(describe(result).copy().withStyle(
                        result == InjectionResult.OK ? ChatFormatting.GREEN : ChatFormatting.RED),
                true);
        return InteractionResult.CONSUME;
    }

    /**
     * The preset the injector item uses, from {@code config/aicorelib.json}.
     * Falls back to {@code aicorelib:guard} when the configured id is not a known preset.
     */
    public static String defaultPreset() {
        String configured = AICoreSettings.get().defaultPreset();
        var id = net.minecraft.resources.ResourceLocation.tryParse(configured);
        return id != null && com.aicorelib.api.AiApi.preset(id) != null
                ? configured
                : AgentRecord.DEFAULT_PRESET;
    }

    /** Turns an injection result into the line the player sees. */
    public static Component describe(InjectionResult result) {
        return switch (result) {
            case OK -> Component.translatable("message.aicorelib.injected");
            case ALREADY -> Component.translatable("message.aicorelib.already_agent");
            case LIMIT_TOTAL -> Component.translatable("message.aicorelib.limit_total");
            case LIMIT_OWNER -> Component.translatable("message.aicorelib.limit_owner");
            case DENIED_TYPE -> Component.translatable("message.aicorelib.denied_type");
        };
    }
}
