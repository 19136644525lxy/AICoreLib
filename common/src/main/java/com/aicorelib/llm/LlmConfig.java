package com.aicorelib.llm;

import com.aicorelib.AICoreLib;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The model configuration, stored as {@code config/aicorelib-llm.json}.
 *
 * <p>The key is stored in plain text because a server admin has to be able to paste one,
 * but it is never logged and never leaves the process except in the Authorization header.
 * If {@code api_key} is blank the value of the environment variable named by
 * {@code api_key_env} is used instead, which is the safer way to run a public server.</p>
 */
public record LlmConfig(
        boolean enabled,
        String baseUrl,
        String model,
        String apiKey,
        String apiKeyEnv,
        double temperature,
        int maxTokens,
        int timeoutSeconds,
        int requestsPerMinute,
        int tokensPerHourGlobal,
        int tokensPerHourAgent,
        int activationRadius,
        boolean requirePlayerNearby,
        int heartbeatTicks,
        int directiveTtlTicks) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile LlmConfig current = defaults();

    public static LlmConfig defaults() {
        return new LlmConfig(false, "https://api.openai.com/v1", "gpt-4o-mini", "", "AICORELIB_API_KEY",
                0.4D, 256, 8, 30, 200_000, 20_000, 32, true, 200, 160);
    }

    public static LlmConfig get() {
        return current;
    }

    public static Path file() {
        return Platform.getConfigFolder().resolve("aicorelib-llm.json");
    }

    /** Reads the config, writing a commented-by-shape default file the first time. */
    public static LlmConfig load() {
        Path path = file();
        try {
            if (!Files.exists(path)) {
                current = defaults();
                save(current);
                AICoreLib.LOGGER.info("Wrote default LLM config to {} (disabled; add an api_key to enable)", path);
                return current;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            LlmConfig fallback = defaults();
            current = new LlmConfig(
                    bool(obj, "enabled", fallback.enabled()),
                    str(obj, "base_url", fallback.baseUrl()),
                    str(obj, "model", fallback.model()),
                    str(obj, "api_key", fallback.apiKey()),
                    str(obj, "api_key_env", fallback.apiKeyEnv()),
                    num(obj, "temperature", fallback.temperature()),
                    integer(obj, "max_tokens", fallback.maxTokens()),
                    integer(obj, "timeout_seconds", fallback.timeoutSeconds()),
                    integer(obj, "requests_per_minute", fallback.requestsPerMinute()),
                    integer(obj, "tokens_per_hour_global", fallback.tokensPerHourGlobal()),
                    integer(obj, "tokens_per_hour_agent", fallback.tokensPerHourAgent()),
                    integer(obj, "activation_radius", fallback.activationRadius()),
                    bool(obj, "require_player_nearby", fallback.requirePlayerNearby()),
                    integer(obj, "heartbeat_ticks", fallback.heartbeatTicks()),
                    integer(obj, "directive_ttl_ticks", fallback.directiveTtlTicks()));
            return current;
        } catch (IOException | RuntimeException e) {
            AICoreLib.LOGGER.error("Could not read {}, keeping the previous LLM config", path, e);
            return current;
        }
    }

    public static void save(LlmConfig config) {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AICoreLib.LOGGER.error("Could not write {}", file(), e);
        }
    }

    /** Key from the config file, falling back to the named environment variable. */
    public String resolvedKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        if (apiKeyEnv == null || apiKeyEnv.isBlank()) {
            return "";
        }
        String fromEnv = System.getenv(apiKeyEnv.trim());
        return fromEnv == null ? "" : fromEnv.trim();
    }

    /** Safe to print: never leaks more than the last four characters. */
    public String maskedKey() {
        String key = resolvedKey();
        if (key.isEmpty()) {
            return "<unset>";
        }
        if (key.length() <= 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }

    public boolean isUsable() {
        return enabled && !resolvedKey().isEmpty() && !baseUrl.isBlank() && !model.isBlank();
    }

    public String endpoint() {
        String base = baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    private static String str(JsonObject obj, String key, String fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : fallback;
    }

    private static double num(JsonObject obj, String key, double fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : fallback;
    }

    private static int integer(JsonObject obj, String key, int fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : fallback;
    }

    @Nullable
    public static String describeChange(LlmConfig before, LlmConfig after) {
        if (before.enabled() != after.enabled()) {
            return after.enabled() ? "LLM enabled" : "LLM disabled";
        }
        if (!before.model().equals(after.model())) {
            return "model changed to " + after.model();
        }
        return null;
    }
}
