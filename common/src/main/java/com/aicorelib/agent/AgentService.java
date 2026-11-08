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
 * Injection / de-injection entry point shared by the injector item and commands.
 */
public final class AgentService {
    private AgentService() {
    }

    public static InteractionResult toggle(Player player, Mob mob) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return InteractionResult.PASS;
        }
        AgentSavedData data = AgentSavedData.get(server);
        UUID id = mob.getUUID();

        if (data.remove(id)) {
            player.displayClientMessage(
                    Component.translatable("message.aicorelib.removed").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }

        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        data.put(id, new AgentRecord(AgentRecord.DEFAULT_PRESET, player.getUUID(), entityType,
                server.overworld().getGameTime()));
        player.displayClientMessage(
                Component.translatable("message.aicorelib.injected").withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }
}
