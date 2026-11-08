package com.aicorelib.llm;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

/**
 * Anything that can answer a chat-completions style request.
 *
 * <p>The contract is intentionally thin so a community could ship a local llama.cpp bridge, a
 * proxy that talks to several vendors, or a test double that returns canned answers, without
 * touching the brain.</p>
 *
 * <p>{@link #complete} must never block the server thread and must never throw: failures come
 * back as {@link LlmResponse#error(String)}.</p>
 */
public interface LlmProvider {
    ResourceLocation id();

    CompletableFuture<LlmResponse> complete(LlmRequest request);
}
