package com.aicorelib.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
