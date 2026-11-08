package com.aicorelib.preset;

import com.aicorelib.api.AiPreset;

import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presets that came from data packs.
 *
 * <p>Kept separate from the code-registered presets in {@code AiApi} so a reload can drop
 * every data-pack entry without touching the built-in fallbacks. Lookup order is
 * data pack first, code second, which is what makes "edit a JSON, run /reload, the mob
 * behaves differently" work without restarting the game.</p>
 */
public final class PresetRegistry {
    private static volatile Map<net.minecraft.resources.ResourceLocation, AiPreset> datapack = Map.of();

    private PresetRegistry() {
    }

    /** Swaps in a freshly parsed batch; called from the reload listener. */
    public static void replaceAll(Map<net.minecraft.resources.ResourceLocation, AiPreset> presets) {
        datapack = new ConcurrentHashMap<>(presets);
    }

    @Nullable
    public static AiPreset fromDataPack(net.minecraft.resources.ResourceLocation id) {
        return datapack.get(id);
    }

    public static Collection<AiPreset> dataPackPresets() {
        return List.copyOf(datapack.values());
    }
}
