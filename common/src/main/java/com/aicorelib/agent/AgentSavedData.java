package com.aicorelib.agent;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide agent registry (UUID -> record), stored next to the level data.
 *
 * <p>Using {@link SavedData} instead of a loader-specific attachment API keeps the
 * two platforms identical and survives chunk unloads for free.</p>
 */
public class AgentSavedData extends SavedData {
    public static final String DATA_NAME = "aicorelib_agents";

    public static final SavedData.Factory<AgentSavedData> FACTORY =
            new SavedData.Factory<>(AgentSavedData::new, AgentSavedData::load, null);

    private final Map<UUID, AgentRecord> agents = new HashMap<>();

    public static AgentSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static AgentSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AgentSavedData data = new AgentSavedData();
        CompoundTag stored = tag.getCompound("agents");
        for (String key : stored.getAllKeys()) {
            try {
                data.agents.put(UUID.fromString(key), AgentRecord.load(stored.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // Corrupted entry: drop it rather than failing the whole save.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag stored = new CompoundTag();
        agents.forEach((uuid, record) -> stored.put(uuid.toString(), record.save()));
        tag.put("agents", stored);
        return tag;
    }

    public Map<UUID, AgentRecord> agents() {
        return Collections.unmodifiableMap(agents);
    }

    public java.util.Set<UUID> ids() {
        return java.util.Set.copyOf(agents.keySet());
    }

    public boolean isInjected(UUID uuid) {
        return agents.containsKey(uuid);
    }

    public AgentRecord get(UUID uuid) {
        return agents.get(uuid);
    }

    /** How many agents a single player owns, for the per-owner budget. */
    public int countOwnedBy(UUID owner) {
        int count = 0;
        for (AgentRecord record : agents.values()) {
            if (owner.equals(record.owner())) {
                count++;
            }
        }
        return count;
    }

    public void put(UUID uuid, AgentRecord record) {
        agents.put(uuid, record);
        setDirty();
    }

    public boolean remove(UUID uuid) {
        if (agents.remove(uuid) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public int clear() {
        int size = agents.size();
        agents.clear();
        if (size > 0) {
            setDirty();
        }
        return size;
    }
}
