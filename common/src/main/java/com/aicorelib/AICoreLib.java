package com.aicorelib;

import com.aicorelib.core.ModCommands;
import com.aicorelib.core.ModItems;
import net.minecraft.resources.ResourceLocation;

/**
 * Common entry point for AICoreLib.
 *
 * <p>Everything that must run on both loaders lives here; platform modules only
 * translate their loader's initialisation hook into a call to {@link #init()}.</p>
 */
public final class AICoreLib {
    public static final String MOD_ID = "aicorelib";

    private AICoreLib() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init() {
        ModItems.init();
        ModCommands.init();
    }
}
