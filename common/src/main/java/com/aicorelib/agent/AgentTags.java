package com.aicorelib.agent;

import com.aicorelib.AICoreLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Entity type tags that decide who may host an AI.
 *
 * <p>{@code #aicorelib:injectable} is an <b>optional allowlist</b>: when the tag is empty or
 * absent every mob is fair game; as soon as a pack puts one entry in it, only listed types
 * qualify. {@code #aicorelib:not_injectable} is a blacklist that always wins — it ships with
 * the ender dragon and the wither, and it is what {@code /aicorelib inject} refuses to touch.</p>
 */
public final class AgentTags {
    public static final TagKey<EntityType<?>> INJECTABLE =
            TagKey.create(Registries.ENTITY_TYPE, AICoreLib.id("injectable"));
    public static final TagKey<EntityType<?>> NOT_INJECTABLE =
            TagKey.create(Registries.ENTITY_TYPE, AICoreLib.id("not_injectable"));

    private AgentTags() {
    }
}
