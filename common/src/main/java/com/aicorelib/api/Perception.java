package com.aicorelib.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Everything the perception layer currently knows about the world around one agent.
 *
 * <p>Filled by {@link Sensor}s a few times per second, read by goals. All fields are
 * server-side only and may be stale by up to one sensor interval — that is deliberate,
 * sensing every tick is what makes AI mods expensive.</p>
 */
public final class Perception {
    private LivingEntity nearestHostile;
    private double nearestHostileDistance = Double.MAX_VALUE;
    private Player owner;
    private double ownerDistance = Double.MAX_VALUE;
    private boolean ownerVisible;
    private int nearbyAllies;
    private long lastHurtTick = Long.MIN_VALUE;
    private UUID lastAttacker;
    private Vec3 home = Vec3.ZERO;

    public LivingEntity nearestHostile() {
        return nearestHostile;
    }

    public double nearestHostileDistance() {
        return nearestHostileDistance;
    }

    public void setNearestHostile(LivingEntity hostile, double distance) {
        this.nearestHostile = hostile;
        this.nearestHostileDistance = distance;
    }

    public Player owner() {
        return owner;
    }

    public double ownerDistance() {
        return ownerDistance;
    }

    public boolean ownerVisible() {
        return ownerVisible;
    }

    public void setOwner(Player owner, double distance, boolean visible) {
        this.owner = owner;
        this.ownerDistance = distance;
        this.ownerVisible = visible;
    }

    public int nearbyAllies() {
        return nearbyAllies;
    }

    public void setNearbyAllies(int nearbyAllies) {
        this.nearbyAllies = nearbyAllies;
    }

    public long lastHurtTick() {
        return lastHurtTick;
    }

    public UUID lastAttacker() {
        return lastAttacker;
    }

    public void setLastHurt(long tick, UUID attacker) {
        this.lastHurtTick = tick;
        this.lastAttacker = attacker;
    }

    public Vec3 home() {
        return home;
    }

    public void setHome(Vec3 home) {
        this.home = home;
    }
}
