package com.aicorelib.llm;

import com.aicorelib.AICoreLib;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registered LLM backends.
 *
 * <p>Only one is active at a time; the config file names it indirectly by being an
 * OpenAI-compatible endpoint. An addon that speaks a different wire protocol registers its
 * own {@link LlmProvider} and calls {@link #use(ResourceLocation)}.</p>
 */
public final class LlmProviders {
    private static final Map<ResourceLocation, LlmProvider> PROVIDERS = new LinkedHashMap<>();
    private static volatile LlmProvider active;

    private LlmProviders() {
    }

    public static void register(LlmProvider provider) {
        PROVIDERS.put(Objects.requireNonNull(provider.id(), "provider id"), provider);
        if (active == null) {
            active = provider;
        }
    }

    public static void use(ResourceLocation id) {
        LlmProvider provider = PROVIDERS.get(id);
        if (provider != null) {
            active = provider;
        } else {
            AICoreLib.LOGGER.warn("No LLM provider registered as {}", id);
        }
    }

    public static LlmProvider get() {
        LlmProvider provider = active;
        if (provider == null) {
            provider = new OpenAiProvider();
            register(provider);
        }
        return provider;
    }

    @Nullable
    public static LlmProvider byId(ResourceLocation id) {
        return PROVIDERS.get(id);
    }

    public static Collection<LlmProvider> all() {
        return java.util.List.copyOf(PROVIDERS.values());
    }
}
