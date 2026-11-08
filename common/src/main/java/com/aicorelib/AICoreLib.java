package com.aicorelib;

import com.aicorelib.agent.AgentRuntime;
import com.aicorelib.core.ModCommands;
import com.aicorelib.core.ModItems;
import com.aicorelib.llm.LlmConfig;
import com.aicorelib.llm.LlmProviders;
import com.aicorelib.llm.OpenAiProvider;
import com.aicorelib.preset.AiActions;
import com.aicorelib.preset.Presets;
import net.minecraft.resources.ResourceLocation;

/**
 * Common entry point for AICoreLib.
 *
 * <p>Everything that must run on both loaders lives here; platform modules only
 * translate their loader's initialisation hook into a call to {@link #init()}.</p>
 */
public final class AICoreLib {
    public static final String MOD_ID = "aicorelib";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("AICoreLib");

    private AICoreLib() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init() {
        ModItems.init();
        ModCommands.init();
        LlmConfig.load();
        LlmProviders.register(new OpenAiProvider());
        AiActions.register();
        Presets.register();
        AgentRuntime.init();
    }
}
