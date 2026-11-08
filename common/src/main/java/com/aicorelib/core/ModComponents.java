package com.aicorelib.core;

import com.aicorelib.AICoreLib;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;

/**
 * Data components added by the library.
 *
 * <p>The core snapshot is a plain {@link CompoundTag} so an old core still loads after the
 * record gains fields: {@link com.aicorelib.agent.AgentRecord#load} fills the gaps.</p>
 */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(AICoreLib.MOD_ID, Registries.DATA_COMPONENT_TYPE);

    public static final RegistrySupplier<DataComponentType<CompoundTag>> AGENT_SNAPSHOT =
            COMPONENTS.register("agent_snapshot", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .build());

    private ModComponents() {
    }

    public static void init() {
        COMPONENTS.register();
    }
}
