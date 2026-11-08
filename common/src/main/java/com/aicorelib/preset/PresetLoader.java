package com.aicorelib.preset;

import com.aicorelib.AICoreLib;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.AiPreset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code data/<namespace>/aicorelib/presets/<name>.json} into {@link PresetRegistry}.
 *
 * <p>The file id becomes the preset id, so {@code bettermod:boss_guard.json} is selectable
 * as {@code bettermod:boss_guard}. A preset may {@code extends} another one, including the
 * built-ins, and only override the parts it cares about. Unknown goal ids are dropped with
 * a warning instead of failing the whole file, so a typo degrades one agent rather than the
 * server's data pack.</p>
 */
public final class PresetLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "aicorelib/presets";

    private static final int MAX_INHERITANCE_DEPTH = 8;
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final ResourceLocation LISTENER_ID = AICoreLib.id("presets");

    public PresetLoader() {
        super(GSON, DIRECTORY);
    }

    public static void register() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new PresetLoader(), LISTENER_ID);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Raw> raws = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            try {
                raws.put(entry.getKey(), Raw.parse(entry.getKey(), entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                AICoreLib.LOGGER.error("Skipping preset {}: {}", entry.getKey(), e.toString());
            }
        }

        Map<ResourceLocation, AiPreset> resolved = new LinkedHashMap<>();
        for (ResourceLocation id : raws.keySet()) {
            resolve(id, raws, resolved, new LinkedHashSet<>(), 0);
        }
        PresetRegistry.replaceAll(resolved);
        AICoreLib.LOGGER.info("Loaded {} AI preset(s) from data packs", resolved.size());
    }

    @Nullable
    private static AiPreset resolve(ResourceLocation id, Map<ResourceLocation, Raw> raws,
                                    Map<ResourceLocation, AiPreset> out, Set<ResourceLocation> chain, int depth) {
        AiPreset cached = out.get(id);
        if (cached != null) {
            return cached;
        }
        Raw raw = raws.get(id);
        if (raw == null) {
            // Not a data pack preset: it must be one registered in code (or missing entirely).
            return AiApi.codePreset(id);
        }
        if (depth > MAX_INHERITANCE_DEPTH) {
            AICoreLib.LOGGER.error("Preset {} inherits deeper than {} levels, ignoring", id, MAX_INHERITANCE_DEPTH);
            return null;
        }
        if (!chain.add(id)) {
            AICoreLib.LOGGER.error("Preset {} is part of an inheritance cycle, ignoring", id);
            return null;
        }

        AiPreset base = null;
        if (raw.parent != null) {
            base = resolve(raw.parent, raws, out, chain, depth + 1);
            if (base == null) {
                AICoreLib.LOGGER.warn("Preset {} extends unknown preset {}, using defaults", id, raw.parent);
            }
        }

        List<AiPreset.GoalWeight> goals = raw.goals != null
                ? raw.goals
                : base != null ? base.goals() : List.of();
        List<AiPreset.GoalWeight> validGoals = new ArrayList<>(goals.size());
        for (AiPreset.GoalWeight weight : goals) {
            if (AiApi.goal(weight.goalId()) == null) {
                AICoreLib.LOGGER.warn("Preset {} references unknown goal {}", id, weight.goalId());
                continue;
            }
            if (weight.weight() <= 0.0D) {
                continue;
            }
            validGoals.add(weight);
        }
        if (validGoals.isEmpty()) {
            AICoreLib.LOGGER.warn("Preset {} has no usable goals; agents using it will stand still", id);
        }

        AiPreset preset = new AiPreset(
                id,
                raw.displayName != null ? raw.displayName : (base != null ? base.displayName() : prettify(id.getPath())),
                validGoals,
                raw.followRange != null ? raw.followRange : (base != null ? base.followRange() : 16.0D),
                raw.speed != null ? raw.speed : (base != null ? base.speed() : 1.0D),
                raw.allowPvp != null ? raw.allowPvp : (base != null && base.allowPvp()),
                raw.decisionInterval != null ? raw.decisionInterval : (base != null ? base.decisionIntervalTicks() : 10));

        chain.remove(id);
        out.put(id, preset);
        return preset;
    }

    private static String prettify(String path) {
        String spaced = path.replace('_', ' ').replace('/', ' ');
        StringBuilder sb = new StringBuilder(spaced.length());
        for (String word : spaced.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.isEmpty() ? path : sb.toString();
    }

    /** Raw JSON view: every field optional except the goals list contents. */
    private static final class Raw {
        final String displayName;
        final @Nullable ResourceLocation parent;
        final @Nullable List<AiPreset.GoalWeight> goals;
        final @Nullable Double followRange;
        final @Nullable Double speed;
        final @Nullable Boolean allowPvp;
        final @Nullable Integer decisionInterval;

        private Raw(String displayName, @Nullable ResourceLocation parent,
                    @Nullable List<AiPreset.GoalWeight> goals, @Nullable Double followRange,
                    @Nullable Double speed, @Nullable Boolean allowPvp, @Nullable Integer decisionInterval) {
            this.displayName = displayName;
            this.parent = parent;
            this.goals = goals;
            this.followRange = followRange;
            this.speed = speed;
            this.allowPvp = allowPvp;
            this.decisionInterval = decisionInterval;
        }

        static Raw parse(ResourceLocation id, JsonObject json) {
            String displayName = json.has("display_name") ? json.get("display_name").getAsString() : null;
            ResourceLocation parent = json.has("extends")
                    ? ResourceLocation.parse(json.get("extends").getAsString())
                    : null;

            List<AiPreset.GoalWeight> goals = null;
            if (json.has("goals")) {
                goals = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("goals");
                for (JsonElement element : array) {
                    JsonObject goal = element.getAsJsonObject();
                    if (!goal.has("goal")) {
                        throw new IllegalArgumentException("goals[] entry without a \"goal\" id");
                    }
                    ResourceLocation goalId = ResourceLocation.parse(goal.get("goal").getAsString());
                    double weight = goal.has("weight") ? goal.get("weight").getAsDouble() : 1.0D;
                    goals.add(new AiPreset.GoalWeight(goalId, weight));
                }
            }

            Double followRange = json.has("follow_range") ? json.get("follow_range").getAsDouble() : null;
            Double speed = json.has("speed") ? json.get("speed").getAsDouble() : null;
            Boolean allowPvp = json.has("allow_pvp") ? json.get("allow_pvp").getAsBoolean() : null;
            Integer decisionInterval = json.has("decision_interval") ? json.get("decision_interval").getAsInt() : null;

            if (followRange != null && (followRange <= 0.0D || followRange > 128.0D)) {
                throw new IllegalArgumentException("follow_range out of range: " + followRange);
            }
            if (speed != null && (speed <= 0.0D || speed > 4.0D)) {
                throw new IllegalArgumentException("speed out of range: " + speed);
            }
            return new Raw(displayName, parent, goals, followRange, speed, allowPvp, decisionInterval);
        }
    }
}
