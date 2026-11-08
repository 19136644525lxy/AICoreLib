package com.aicorelib.agent;

import com.aicorelib.core.ModComponents;
import com.aicorelib.core.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Core capture and transfer: the "move an AI into a new body" feature.
 *
 * <p>Rules (see DESIGN.md 搂7.5): a core only drops when a player 鈥?or an agent owned by a
 * player 鈥?lands the killing blow, a transfer keeps 60% of the memory and costs 20
 * affection, and a lineage may move at most {@link AgentRecord#MAX_HOPS} times. Past that
 * the core is damaged and can no longer be installed.</p>
 */
public final class AgentCore {
    private static final double MEMORY_KEEP = 0.6D;
    private static final int AFFECTION_COST = 20;

    private AgentCore() {
    }

    public static ItemStack capture(AgentRecord record) {
        ItemStack stack = new ItemStack(ModItems.AI_CORE.get());
        stack.set(ModComponents.AGENT_SNAPSHOT.get(), record.save());
        return stack;
    }

    @Nullable
    public static AgentRecord snapshot(ItemStack stack) {
        CompoundTag tag = stack.get(ModComponents.AGENT_SNAPSHOT.get());
        return tag == null ? null : AgentRecord.load(tag);
    }

    public static boolean isDamaged(AgentRecord record) {
        return record.hops() >= AgentRecord.MAX_HOPS;
    }

    /** Installs the core's AI into {@code mob}, consuming the core on success. */
    public static boolean transfer(Player player, Mob mob, ItemStack stack) {
        MinecraftServer server = player.getServer();
        AgentRecord snapshot = snapshot(stack);
        if (server == null || snapshot == null) {
            return false;
        }
        if (isDamaged(snapshot)) {
            player.displayClientMessage(Component.translatable("message.aicorelib.core_damaged")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (AgentSavedData.get(server).isInjected(mob.getUUID())) {
            player.displayClientMessage(Component.translatable("message.aicorelib.already_agent")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        AgentRecord moved = new AgentRecord(
                snapshot.presetId(),
                player.getUUID(),
                entityType,
                server.overworld().getGameTime(),
                mob.position(),
                snapshot.hops() + 1,
                keepMemory(snapshot.memorySummary()),
                Math.max(0, snapshot.affection() - AFFECTION_COST));

        if (!AgentService.inject(mob, moved)) {
            return false;
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.aicorelib.core_installed")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private static String keepMemory(String memory) {
        int keep = (int) Math.floor(memory.length() * MEMORY_KEEP);
        return memory.length() <= keep ? memory : memory.substring(0, keep);
    }
}

