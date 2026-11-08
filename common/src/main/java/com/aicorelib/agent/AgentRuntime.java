package com.aicorelib.agent;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives every live agent once per server tick.
 *
 * <p>Attachment is re-done from two directions: {@link EntityEvent#ADD} covers mobs that
 * enter the world (chunk load, respawn, {@code /summon}), and a slow sweep every 20 ticks
 * covers the ones that were already loaded when the server booted, because those do not
 * fire ADD.</p>
 */
public final class AgentRuntime {
    private static final int SWEEP_INTERVAL = 20;

    private static final Map<UUID, AgentHandle> HANDLES = new LinkedHashMap<>();
    private static boolean initialised;
    private static int sweepCooldown;

    private AgentRuntime() {
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        TickEvent.SERVER_POST.register(AgentRuntime::onServerTick);
        EntityEvent.ADD.register((entity, level) -> {
            if (level instanceof ServerLevel && entity instanceof Mob mob) {
                attachIfInjected(mob);
            }
            return EventResult.pass();
        });
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob) {
                onDeath(mob, source);
            }
            return EventResult.pass();
        });
    }

    @Nullable
    public static AgentHandle handle(Mob mob) {
        return HANDLES.get(mob.getUUID());
    }

    /** Registers a freshly injected mob and installs its goal. */
    public static void track(Mob mob, AgentRecord record) {
        AgentHandle existing = HANDLES.get(mob.getUUID());
        if (existing != null) {
            existing.setRecord(record);
            existing.attach();
            return;
        }
        AgentHandle handle = new AgentHandle(mob, record);
        HANDLES.put(mob.getUUID(), handle);
        handle.attach();
    }

    /** Releases a mob: remove the goal first so vanilla behaviour resumes immediately. */
    public static void untrack(Mob mob) {
        AgentHandle handle = HANDLES.remove(mob.getUUID());
        if (handle != null) {
            handle.detach();
        }
    }

    private static void attachIfInjected(Mob mob) {
        MinecraftServer server = mob.getServer();
        if (server == null || HANDLES.containsKey(mob.getUUID())) {
            return;
        }
        AgentRecord record = AgentSavedData.get(server).get(mob.getUUID());
        if (record != null) {
            track(mob, record);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (HANDLES.isEmpty() && --sweepCooldown > 0) {
            return;
        }
        long now = server.getTickCount();

        for (AgentHandle handle : new ArrayList<>(HANDLES.values())) {
            if (!handle.isValid()) {
                handle.detach();
                HANDLES.remove(handle.mob().getUUID());
                continue;
            }
            handle.tick(now);
        }

        if (--sweepCooldown <= 0) {
            sweepCooldown = SWEEP_INTERVAL;
            sweep(server);
        }
    }

    /** Picks up injected mobs that were already loaded when the server started. */
    private static void sweep(MinecraftServer server) {
        AgentSavedData data = AgentSavedData.get(server);
        for (UUID id : data.ids()) {
            if (HANDLES.containsKey(id)) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof Mob mob) {
                    AgentRecord record = data.get(id);
                    if (record != null) {
                        track(mob, record);
                    }
                    break;
                }
            }
        }
    }

    private static void onDeath(Mob mob, DamageSource source) {
        AgentHandle handle = HANDLES.remove(mob.getUUID());
        if (handle == null) {
            return;
        }
        handle.detach();
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return;
        }
        AgentSavedData data = AgentSavedData.get(server);
        AgentRecord record = data.get(mob.getUUID());
        data.remove(mob.getUUID());
        if (record == null || !killedByPlayer(server, source)) {
            return;
        }
        ItemEntity drop = new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.5D, mob.getZ(),
                AgentCore.capture(record));
        drop.setDefaultPickUpDelay();
        mob.level().addFreshEntity(drop);
    }

    /** A core only drops when a player, or an agent owned by a player, lands the killing blow. */
    private static boolean killedByPlayer(MinecraftServer server, DamageSource source) {
        if (source.getEntity() instanceof Player) {
            return true;
        }
        if (source.getEntity() instanceof Mob killer) {
            AgentRecord killerRecord = AgentSavedData.get(server).get(killer.getUUID());
            return killerRecord != null && killerRecord.owner() != null;
        }
        return false;
    }
}
