package com.aicorelib.llm;

import com.aicorelib.AICoreLib;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to anything that speaks the OpenAI chat-completions dialect.
 *
 * <p>That covers OpenAI itself, Azure-style proxies, DeepSeek, Moonshot, Ollama, LM Studio and
 * most gateways — which is why this is the only provider the library ships. A vendor with its
 * own protocol can implement {@link LlmProvider} without touching this class.</p>
 */
public final class OpenAiProvider implements LlmProvider {
    public static final ResourceLocation ID = AICoreLib.id("openai");
    private static final Gson GSON = new Gson();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public CompletableFuture<LlmResponse> complete(LlmRequest request) {
        LlmConfig config = LlmConfig.get();
        String key = config.resolvedKey();
        if (key.isEmpty()) {
            return CompletableFuture.completedFuture(LlmResponse.error("no api key configured"));
        }

        HttpRequest http;
        try {
            http = HttpRequest.newBuilder(URI.create(config.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .timeout(Duration.ofSeconds(Math.max(1, request.timeoutSeconds())))
                    .POST(HttpRequest.BodyPublishers.ofString(encode(request), StandardCharsets.UTF_8))
                    .build();
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(LlmResponse.error("bad endpoint: " + e.getMessage()));
        }

        return client.sendAsync(http, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> decode(response.statusCode(), response.body()))
                .exceptionally(t -> LlmResponse.error(describe(t)));
    }

    private static String encode(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.model());
        body.addProperty("temperature", request.temperature());
        body.addProperty("max_tokens", request.maxTokens());

        JsonArray messages = new JsonArray();
        for (LlmMessage message : request.messages()) {
            messages.add(encodeMessage(message));
        }
        body.add("messages", messages);

        if (request.tools() != null && !request.tools().isEmpty()) {
            body.add("tools", request.tools());
            body.addProperty("tool_choice", "auto");
        }
        return GSON.toJson(body);
    }

    private static JsonObject encodeMessage(LlmMessage message) {
        JsonObject json = new JsonObject();
        json.addProperty("role", message.role());
        if (message.content() != null) {
            json.addProperty("content", message.content());
        }
        if (message.toolCallId() != null) {
            json.addProperty("tool_call_id", message.toolCallId());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            JsonArray calls = new JsonArray();
            for (LlmMessage.ToolCall call : message.toolCalls()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", call.id());
                entry.addProperty("type", "function");
                JsonObject function = new JsonObject();
                function.addProperty("name", call.name());
                function.addProperty("arguments", GSON.toJson(call.arguments()));
                entry.add("function", function);
                calls.add(entry);
            }
            json.add("tool_calls", calls);
        }
        return json;
    }

    private static LlmResponse decode(int status, String body) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            root = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            root = null;
        }

        if (status < 200 || status >= 300) {
            String detail = root != null && root.has("error") ? root.get("error").toString() : body;
            if (detail.length() > 200) {
                detail = detail.substring(0, 200) + "…";
            }
            return LlmResponse.error("HTTP " + status + ": " + detail.replaceAll("\\s+", " "));
        }
        if (root == null || !root.has("choices") || root.getAsJsonArray("choices").isEmpty()) {
            return LlmResponse.error("malformed response: no choices");
        }

        JsonObject message = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message");
        if (message == null) {
            return LlmResponse.error("malformed response: no message");
        }

        String content = nullableString(message, "content");
        List<LlmMessage.ToolCall> calls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject call = element.getAsJsonObject();
                JsonObject function = call.getAsJsonObject("function");
                if (function == null) {
                    continue;
                }
                JsonObject arguments = new JsonObject();
                String raw = nullableString(function, "arguments");
                if (raw != null && !raw.isBlank()) {
                    try {
                        JsonElement parsedArgs = JsonParser.parseString(raw);
                        if (parsedArgs.isJsonObject()) {
                            arguments = parsedArgs.getAsJsonObject();
                        }
                    } catch (RuntimeException ignored) {
                        // A model that emits broken JSON for the arguments is a normal failure
                        // mode; drop the args and let the action decide (most reject then).
                    }
                }
                calls.add(new LlmMessage.ToolCall(nullableString(call, "id"), nullableString(function, "name"), arguments));
            }
        }

        int promptTokens = 0;
        int completionTokens = 0;
        if (root.has("usage") && root.get("usage").isJsonObject()) {
            JsonObject usage = root.getAsJsonObject("usage");
            promptTokens = intOr(usage, "prompt_tokens");
            completionTokens = intOr(usage, "completion_tokens");
        }
        return new LlmResponse(content, calls, promptTokens, completionTokens, null);
    }

    @Nullable
    private static String nullableString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static int intOr(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
    }

    private static String describe(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
