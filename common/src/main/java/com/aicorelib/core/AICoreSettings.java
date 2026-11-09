package com.aicorelib.core;

import com.aicorelib.AICoreLib;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-wide limits, stored as {@code config/aicorelib.json}.
 *
 * <p>Kept separate from {@link com.aicorelib.llm.LlmConfig} on purpose: one file is about how
 * many agents a server allows, the other is about money. Admins usually change the first once
 * and the second never.</p>
 *
 * <p>Which entities may be injected is <b>not</b> a setting but a pair of entity type tags
 * ({@code #aicorelib:injectable} / {@code #aicorelib:not_injectable}), so packs can override it
 * per world without touching the config.</p>
 */
public record AICoreSettings(
        int maxAgentsTotal,
        int maxAgentsPerOwner,
        String defaultPreset) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile AICoreSettings current = defaults();

    public static AICoreSettings defaults() {
        return new AICoreSettings(256, 16, "aicorelib:guard");
    }

    public static AICoreSettings get() {
        return current;
    }

    public static Path file() {
        return Platform.getConfigFolder().resolve("aicorelib.json");
    }

    public static AICoreSettings load() {
        Path path = file();
        try {
            if (!Files.exists(path)) {
                current = defaults();
                save(current);
                return current;
            }
            JsonObject obj = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            AICoreSettings fallback = defaults();
            current = new AICoreSettings(
                    Math.max(0, integer(obj, "max_agents_total", fallback.maxAgentsTotal())),
                    Math.max(0, integer(obj, "max_agents_per_owner", fallback.maxAgentsPerOwner())),
                    str(obj, "default_preset", fallback.defaultPreset()));
            return current;
        } catch (IOException | RuntimeException e) {
            AICoreLib.LOGGER.error("Could not read {}, keeping the previous limits", path, e);
            return current;
        }
    }

    public static void save(AICoreSettings settings) {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AICoreLib.LOGGER.error("Could not write {}", file(), e);
        }
    }

    /**
     * @return the number of agents this owner may still add, or {@link Integer#MAX_VALUE}
     *         when there is no per-owner limit
     */
    public int remainingFor(int owned, int total) {
        if (maxAgentsTotal > 0 && total >= maxAgentsTotal) {
            return 0;
        }
        if (maxAgentsPerOwner > 0) {
            return Math.max(0, maxAgentsPerOwner - owned);
        }
        return Integer.MAX_VALUE;
    }

    private static String str(JsonObject obj, String key, String fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject obj, String key, int fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : fallback;
    }
}
