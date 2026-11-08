package com.aicorelib.agent;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Everything persisted about one injected agent.
 *
 * @param presetId   which AI preset drives this agent
 * @param owner      the player that injected it (may be {@code null} on legacy data)
 * @param entityType registry id of the entity type, kept for debugging and for
 *                   restoring a core onto a different mob
 * @param injectedAt game time (in ticks) of the injection
 */
public record AgentRecord(String presetId, UUID owner, String entityType, long injectedAt) {
    public static final String DEFAULT_PRESET = "aicorelib:guard";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("preset", presetId);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("entity_type", entityType);
        tag.putLong("injected_at", injectedAt);
        return tag;
    }

    public static AgentRecord load(CompoundTag tag) {
        UUID owner = tag.contains("owner") ? tag.getUUID("owner") : null;
        return new AgentRecord(
                tag.getString("preset"),
                owner,
                tag.getString("entity_type"),
                tag.getLong("injected_at"));
    }
}
