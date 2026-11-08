package com.aicorelib.llm;

import com.google.gson.JsonArray;

import java.util.List;

/**
 * A single completion request.
 *
 * <p>{@code tools} is passed through verbatim: the library builds it from the
 * {@link com.aicorelib.api.AiAction} schemas, but an addon replacing the provider may want to
 * add or drop tools, so the field stays a raw {@link JsonArray}.</p>
 */
public record LlmRequest(String model, List<LlmMessage> messages, JsonArray tools,
        double temperature, int maxTokens, int timeoutSeconds) {
}
