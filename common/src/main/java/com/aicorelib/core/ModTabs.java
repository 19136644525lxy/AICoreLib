package com.aicorelib.core;

import com.aicorelib.AICoreLib;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The creative tab that holds the library's own items. Addons are expected to add their
 * own tab rather than piggy-backing on this one.
 */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(AICoreLib.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.aicorelib"))
                    .icon(() -> new ItemStack(ModItems.AI_INJECTOR.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.AI_INJECTOR.get());
                        output.accept(ModItems.AI_CORE.get());
                    })
                    .build());

    private ModTabs() {
    }

    public static void init() {
        TABS.register();
    }
}
