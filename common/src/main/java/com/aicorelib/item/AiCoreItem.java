package com.aicorelib.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The AI Core: a portable snapshot of an agent.
 *
 * <p>M0 only registers the item; snapshot capture/restore lands in M1 together with
 * the death-drop rule.</p>
 */
public class AiCoreItem extends Item {
    public AiCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aicorelib.ai_core.empty").withStyle(ChatFormatting.DARK_GRAY));
    }
}
