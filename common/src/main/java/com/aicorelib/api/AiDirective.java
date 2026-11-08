package com.aicorelib.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One instruction produced by the remote brain.
 *
 * <p>Directives are short lived on purpose. A model that answered two seconds ago usually
 * still knows what is happening; one that answered thirty seconds ago is guessing, and a
 * guard that keeps chasing a corpse for half a minute looks broken. When a directive
 * expires, {@code LocalBrain} takes the wheel again without any hand-over code.</p>
 *
 * @param actionId  which {@link AiAction} to run
 * @param args      arguments in the action's own schema
 * @param issuedAt  server tick the directive was created
 * @param expiresAt server tick after which it is ignored
 */
public record AiDirective(ResourceLocation actionId, CompoundTag args, long issuedAt, long expiresAt) {

    public AiDirective {
        args = args == null ? new CompoundTag() : args;
    }

    public boolean expired(long now) {
        return now > expiresAt;
    }
}
