package com.aicorelib.core;

import com.aicorelib.agent.AgentRecord;
import com.aicorelib.agent.AgentSavedData;
import com.aicorelib.agent.AgentService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Admin/debug commands.
 *
 * <pre>
 * /aicorelib list                      - list every injected agent
 * /aicorelib inject &lt;targets&gt; [preset] - attach the AI to mobs without the item
 * /aicorelib remove &lt;targets&gt;          - release mobs from the AI
 * /aicorelib clear                     - remove every injected agent
 * </pre>
 */
public final class ModCommands {
    private ModCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                dispatcher.register(Commands.literal("aicorelib")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list").executes(ctx -> {
                            AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
                            Map<UUID, AgentRecord> agents = data.agents();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AICoreLib] injected agents: " + agents.size()), false);
                            agents.forEach((uuid, record) -> ctx.getSource().sendSuccess(
                                    () -> Component.literal(" - " + uuid + " | " + record.entityType()
                                            + " | preset=" + record.presetId()), false));
                            return agents.size();
                        }))
                        .then(Commands.literal("inject")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> inject(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets"),
                                                AgentRecord.DEFAULT_PRESET))
                                        .then(Commands.argument("preset", StringArgumentType.string())
                                                .executes(ctx -> inject(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "preset"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> remove(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets")))))
                        .then(Commands.literal("clear").executes(ctx -> {
                            AgentSavedData data = AgentSavedData.get(ctx.getSource().getServer());
                            int removed = data.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AICoreLib] cleared " + removed + " agent(s)"), true);
                            return removed;
                        }))));
    }

    private static int inject(CommandSourceStack source, Collection<? extends Entity> targets, String preset)
            throws CommandSyntaxException {
        Entity executor = source.getEntity();
        UUID owner = executor == null ? null : executor.getUUID();
        int count = 0;
        for (Entity target : targets) {
            if (target instanceof Mob mob && AgentService.inject(mob, owner, preset)) {
                count++;
            }
        }
        int injected = count;
        source.sendSuccess(() -> Component.literal("[AICoreLib] injected " + injected + " mob(s) as " + preset), true);
        return count;
    }

    private static int remove(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity target : targets) {
            if (target instanceof Mob mob && AgentService.remove(mob)) {
                count++;
            }
        }
        int removed = count;
        source.sendSuccess(() -> Component.literal("[AICoreLib] released " + removed + " mob(s)"), true);
        return count;
    }
}
