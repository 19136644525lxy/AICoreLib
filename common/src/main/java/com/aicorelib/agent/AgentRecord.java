package com.aicorelib.agent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Everything persisted about one injected agent.
 *
 * @param presetId      which AI preset drives this agent
 * @param owner         the player that injected it (may be {@code null} on legacy data)
 * @param entityType    registry id of the entity type, kept for debugging and for
 *                      restoring a core onto a different mob
 * @param injectedAt    game time (in ticks) of the injection
 * @param home          where the agent was created; sentries patrol around it
 * @param hops          how many times this AI has migrated between bodies
 * @param memorySummary short human readable memory carried through core migration
 * @param affection     0-100, used by social addons (Monsterheart et al.)
 */
public record AgentRecord(
        String presetId,
        @Nullable UUID owner,
        String entityType,
        long injectedAt,
        @Nullable Vec3 home,
        int hops,
        String memorySummary,
        int affection) {

    public static final String DEFAULT_PRESET = "aicorelib:guard";

    /** Hard limit on body swaps, so cores cannot be farmed forever. */
    public static final int MAX_HOPS = 3;

    public static AgentRecord create(String presetId, @Nullable UUID owner, String entityType,
                                     long injectedAt, @Nullable Vec3 home) {
        return new AgentRecord(presetId, owner, entityType, injectedAt, home, 0, "", 0);
    }

    public AgentRecord movedTo(@Nullable UUID newOwner, long when, @Nullable Vec3 newHome) {
        return new AgentRecord(presetId, newOwner, entityType, when, newHome, hops, memorySummary, affection);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("preset", presetId);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("entity_type", entityType);
        tag.putLong("injected_at", injectedAt);
        if (home != null) {
            tag.putDouble("home_x", home.x);
            tag.putDouble("home_y", home.y);
            tag.putDouble("home_z", home.z);
        }
        tag.putInt("hops", hops);
        tag.putString("memory", memorySummary);
        tag.putInt("affection", affection);
        return tag;
    }

    public static AgentRecord load(CompoundTag tag) {
        UUID owner = tag.contains("owner") ? tag.getUUID("owner") : null;
        Vec3 home = tag.contains("home_x")
                ? new Vec3(tag.getDouble("home_x"), tag.getDouble("home_y"), tag.getDouble("home_z"))
                : null;
        return new AgentRecord(
                tag.getString("preset"),
                owner,
                tag.getString("entity_type"),
                tag.getLong("injected_at"),
                home,
                tag.getInt("hops"),
                tag.getString("memory"),
                tag.getInt("affection"));
    }
}
