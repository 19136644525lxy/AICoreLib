package com.aicorelib.core;

import com.aicorelib.AICoreLib;
import com.aicorelib.item.AiCoreItem;
import com.aicorelib.item.AiInjectorItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * All items registered by the library.
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(AICoreLib.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> AI_INJECTOR = ITEMS.register("ai_injector",
            () -> new AiInjectorItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)
                    .fireResistant()));

    public static final RegistrySupplier<Item> AI_CORE = ITEMS.register("ai_core",
            () -> new AiCoreItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.UNCOMMON)
                    .fireResistant()));

    private ModItems() {
    }

    public static void init() {
        ITEMS.register();
        ModComponents.init();
    }
}
