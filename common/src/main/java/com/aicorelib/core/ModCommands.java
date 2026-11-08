package com.aicorelib.core;

import com.aicorelib.agent.AgentRecord;
import com.aicorelib.agent.AgentSavedData;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Admin/debug commands.
 *
 * <pre>
 * /aicorelib list   - list every injected agent
 * /aicorelib clear  - remove every injected agent
 * </pre>
 */
public final class ModCommands {
    private ModCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                dispatcher.register(Commands.literal("aicorelib")
                        .then(Commands.literal("list").executes(ctx -> {
                            AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
                            Map<java.util.UUID, AgentRecord> agents = data.agents();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AICoreLib] injected agents: " + agents.size()), false);
                            agents.forEach((uuid, record) -> ctx.getSource().sendSuccess(
                                    () -> Component.literal(" - " + uuid + " | " + record.entityType()
                                            + " | preset=" + record.presetId()), false));
                            return agents.size();
                        }))
                        .then(Commands.literal("clear").executes(ctx -> {
                            AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
                            int removed = data.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AICoreLib] cleared " + removed + " agent(s)"), true);
                            return removed;
                        }))));
    }
}
