package com.aicorelib.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handed to every {@link Sensor}, {@link UtilityGoal} and {@link AiAction}.
 *
 * <p>Mutable by design: sensors write into {@link #perception()}, goals read from it.
 * The mob is only ever touched from the server thread, which is where all of this runs.</p>
 */
public final class AgentContext {
    private final Mob mob;
    private final ResourceLocation presetId;
    private final UUID ownerId;
    private final int hops;
    private final Perception perception = new Perception();
    private final Map<String, Object> extras = new HashMap<>();
    /**
     * Short handles ("e1", "e2"…) bound to entity ids for the current prompt.
     *
     * <p>Models handle short strings far better than UUIDs, and a stale handle resolving to a
     * dead entity is a cheap no-op instead of a hallucinated target.</p>
     */
    private final Map<String, UUID> refs = new ConcurrentHashMap<>();
    private long tick;

    public AgentContext(Mob mob, ResourceLocation presetId, @Nullable UUID ownerId, int hops) {
        this.mob = mob;
        this.presetId = presetId;
        this.ownerId = ownerId;
        this.hops = hops;
    }

    public Mob mob() {
        return mob;
    }

    public MinecraftServer server() {
        return mob.getServer();
    }

    public ResourceLocation presetId() {
        return presetId;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    public Perception perception() {
        return perception;
    }

    /** The preset currently driving this agent, or {@code null} if it was removed. */
    @Nullable
    public AiPreset preset() {
        return AiApi.preset(presetId);
    }

    /** Movement speed for this agent, with a sane fallback. */
    public double speed() {
        AiPreset preset = preset();
        return preset == null ? 1.0D : preset.speed();
    }

    public Map<String, Object> extras() {
        return extras;
    }

    public Map<String, UUID> refs() {
        return refs;
    }

    /** Replaces the short handles used by the next prompt. */
    public void setRefs(Map<String, UUID> handles) {
        refs.clear();
        refs.putAll(handles);
    }

    /**
     * Resolves a handle such as {@code "e1"} to a live entity, or {@code null} if it died,
     * went to another dimension, or was never bound.
     */
    @Nullable
    public Entity entityByRef(String handle) {
        if (handle == null || handle.isBlank()) {
            return null;
        }
        UUID id = refs.get(handle.trim());
        if (id == null) {
            return null;
        }
        MinecraftServer server = server();
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }

    /** Server game time of the current tick, refreshed by the runtime. */
    public long tick() {
        return tick;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    public UUID id() {
        return mob.getUUID();
    }
}
