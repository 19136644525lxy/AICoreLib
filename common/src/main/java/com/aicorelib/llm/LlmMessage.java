package com.aicorelib.llm;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/** One entry of the chat-completions message list. */
public record LlmMessage(String role, @Nullable String content, @Nullable List<ToolCall> toolCalls,
        @Nullable String toolCallId) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null);
    }

    public static LlmMessage assistant(String content, @Nullable List<ToolCall> calls) {
        return new LlmMessage("assistant", content, calls, null);
    }

    public static LlmMessage tool(String toolCallId, String content) {
        return new LlmMessage("tool", content, null, toolCallId);
    }

    /** A tool call as the model emitted it, before we validate the arguments. */
    public record ToolCall(String id, String name, JsonObject arguments) {
        public ToolCall {
            arguments = arguments == null ? new JsonObject() : arguments;
        }
    }
}
