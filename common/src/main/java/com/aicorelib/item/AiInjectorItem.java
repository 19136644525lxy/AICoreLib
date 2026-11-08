package com.aicorelib.item;

import com.aicorelib.agent.AgentService;
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

import java.util.List;

/**
 * The AI Injector: right-click a mob to connect it to (or disconnect it from) the preset AI.
 */
public class AiInjectorItem extends Item {
    public AiInjectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob)) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.aicorelib.not_a_mob").withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResult.FAIL;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return AgentService.toggle(player, mob);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aicorelib.ai_injector.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.aicorelib.ai_injector.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
