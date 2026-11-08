package com.aicorelib.llm;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * The model's answer.
 *
 * <p>Errors are values, not exceptions: a dead network must degrade to "the local brain keeps
 * running", never to a stack trace on the server thread.</p>
 */
public record LlmResponse(@Nullable String content, List<LlmMessage.ToolCall> toolCalls,
        int promptTokens, int completionTokens, @Nullable String error) {

    public static LlmResponse error(String message) {
        return new LlmResponse(null, List.of(), 0, 0, message);
    }

    public boolean ok() {
        return error == null;
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    /** The first tool call, if the model asked for one. */
    @Nullable
    public LlmMessage.ToolCall firstToolCall() {
        return toolCalls.isEmpty() ? null : toolCalls.get(0);
    }
}
