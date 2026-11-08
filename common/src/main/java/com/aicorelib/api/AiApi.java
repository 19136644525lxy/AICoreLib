package com.aicorelib.api;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one place third-party code registers its extension points.
 *
 * <p>Everything here is thread-safe-ish in the sense that it is only ever called during
 * mod initialisation, before any server starts. Re-registering the same id replaces the
 * previous entry, which makes reloads and hot-swapping painless.</p>
 */
public final class AiApi {
    private static final Map<ResourceLocation, UtilityGoal> GOALS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, AiAction> ACTIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, AiPreset> PRESETS = new LinkedHashMap<>();
    private static final List<Sensor> SENSORS = new ArrayList<>();

    private AiApi() {
    }

    public static void registerGoal(UtilityGoal goal) {
        GOALS.put(Objects.requireNonNull(goal.id(), "goal id"), goal);
    }

    public static void registerAction(AiAction action) {
        ACTIONS.put(Objects.requireNonNull(action.id(), "action id"), action);
    }

    public static void registerPreset(AiPreset preset) {
        PRESETS.put(Objects.requireNonNull(preset.id(), "preset id"), preset);
    }

    public static void registerSensor(Sensor sensor) {
        SENSORS.add(sensor);
        SENSORS.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    }

    @Nullable
    public static UtilityGoal goal(ResourceLocation id) {
        return GOALS.get(id);
    }

    @Nullable
    public static AiAction action(ResourceLocation id) {
        return ACTIONS.get(id);
    }

    @Nullable
    public static AiPreset preset(ResourceLocation id) {
        return PRESETS.get(id);
    }

    public static Collection<UtilityGoal> goals() {
        return List.copyOf(GOALS.values());
    }

    public static Collection<AiAction> actions() {
        return List.copyOf(ACTIONS.values());
    }

    public static Collection<AiPreset> presets() {
        return List.copyOf(PRESETS.values());
    }

    public static List<Sensor> sensors() {
        return List.copyOf(SENSORS);
    }
}
