package com.aicorelib.agent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

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
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return false;
        }
        AgentSavedData data = AgentSavedData.get(server);
        UUID id = mob.getUUID();
        if (data.isInjected(id)) {
            return false;
        }
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        data.put(id, new AgentRecord(presetId, owner, entityType, server.overworld().getGameTime()));
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
        return AgentSavedData.get(server).remove(mob.getUUID());
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

        inject(mob, player.getUUID(), AgentRecord.DEFAULT_PRESET);
        player.displayClientMessage(
                Component.translatable("message.aicorelib.injected").withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }
}
