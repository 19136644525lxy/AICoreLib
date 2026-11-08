package com.aicorelib.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A single callable capability.
 *
 * <p>Goals call actions directly; the LLM layer (M3) exposes the same actions as
 * function-calling tools, which is why every action carries a JSON schema for its
 * arguments. Arguments are kept as a {@link CompoundTag} so both paths share one shape.</p>
 */
public interface AiAction {
    ResourceLocation id();

    /** Human readable one-liner, also used in the LLM tool description. */
    String description();

    /** JSON Schema (as a JSON string) describing {@code args}. */
    default String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    default boolean canRun(AgentContext ctx) {
        return true;
    }

    void run(AgentContext ctx, CompoundTag args);
}
