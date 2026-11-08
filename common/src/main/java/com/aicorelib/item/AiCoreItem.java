package com.aicorelib.item;

import com.aicorelib.agent.AgentCore;
import com.aicorelib.agent.AgentRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * The AI Core: a portable snapshot of an agent. Right-click a mob to install the stored AI.
 *
 * <p>Installing keeps 60% of the memory, costs 20 affection and increments the lineage hop
 * counter; see {@link AgentCore}.</p>
 */
public class AiCoreItem extends Item {
    public AiCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Mob mob)) {
            return InteractionResult.PASS;
        }
        AgentRecord snapshot = AgentCore.snapshot(stack);
        if (snapshot == null) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return AgentCore.transfer(player, mob, stack) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        AgentRecord snapshot = AgentCore.snapshot(stack);
        if (snapshot == null) {
            tooltip.add(Component.translatable("tooltip.aicorelib.ai_core.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.aicorelib.ai_core.preset", snapshot.presetId())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.aicorelib.ai_core.lineage",
                        snapshot.hops(), AgentRecord.MAX_HOPS)
                .withStyle(AgentCore.isDamaged(snapshot) ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        if (AgentCore.isDamaged(snapshot)) {
            tooltip.add(Component.translatable("tooltip.aicorelib.ai_core.damaged")
                    .withStyle(ChatFormatting.RED));
        }
        if (!snapshot.memorySummary().isEmpty()) {
            tooltip.add(Component.literal("\"" + snapshot.memorySummary() + "\"")
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        }
    }
}

