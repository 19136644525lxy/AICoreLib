package com.aicorelib.agent;

import com.aicorelib.AICoreLib;
import com.aicorelib.api.AgentContext;
import com.aicorelib.mixin.MobGoalSelectorAccessor;
import com.aicorelib.api.AiApi;
import com.aicorelib.api.Perception;
import com.aicorelib.api.Sensor;
import com.aicorelib.brain.AgentGoal;
import com.aicorelib.brain.LocalBrain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * One injected mob, its brain, its context and the goal that claims its flags.
 *
 * <p>Lives only on the server. Created when a mob is injected or when a chunk with an
 * injected mob loads; dropped when the mob dies or the agent is released.</p>
 */
public final class AgentHandle {
    private static final int SENSOR_INTERVAL = 10;

    private final Mob mob;
    private final AgentContext context;
    private final LocalBrain brain;
    private final AgentGoal goal;
    private AgentRecord record;
    private int sensorCooldown;
    private boolean attached;
    private boolean faulted;

    public AgentHandle(Mob mob, AgentRecord record) {
        this.mob = mob;
        this.record = record;
        ResourceLocation presetId = ResourceLocation.tryParse(record.presetId());
        if (presetId == null) {
            presetId = ResourceLocation.fromNamespaceAndPath("aicorelib", "blank");
        }
        this.context = new AgentContext(mob, presetId, record.owner(), record.hops());
        this.brain = new LocalBrain(context);
        this.goal = new AgentGoal(this);
        if (record.home() != null) {
            context.perception().setHome(record.home());
        } else {
            context.perception().setHome(mob.position());
        }
    }

    public Mob mob() {
        return mob;
    }

    public AgentContext context() {
        return context;
    }

    public LocalBrain brain() {
        return brain;
    }

    public AgentRecord record() {
        return record;
    }

    public void setRecord(AgentRecord record) {
        this.record = record;
    }

    /** Installs the claiming goal. Idempotent. */
    public void attach() {
        if (attached) {
            return;
        }
        ((MobGoalSelectorAccessor) mob).aicorelib$goalSelector().addGoal(0, goal);
        attached = true;
    }

    /** Removes the claiming goal, letting vanilla behaviour take over again. */
    public void detach() {
        if (!attached) {
            return;
        }
        ((MobGoalSelectorAccessor) mob).aicorelib$goalSelector().removeGoal(goal);
        brain.stopActiveGoal();
        attached = false;
    }

    public boolean isValid() {
        return mob.isAlive() && !mob.isRemoved() && mob.level() != null;
    }

    /** Called once per server tick. */
    public void tick(long now) {
        context.setTick(now);
        try {
            if (--sensorCooldown <= 0) {
                sensorCooldown = SENSOR_INTERVAL;
                Perception perception = context.perception();
                for (Sensor sensor : AiApi.sensors()) {
                    sensor.sample(context);
                }
                if (record.home() == null) {
                    perception.setHome(mob.position());
                }
            }
            brain.tick(now);
        } catch (RuntimeException e) {
            // A misbehaving third-party goal must never take the server down.
            if (!faulted) {
                faulted = true;
                AICoreLib.LOGGER.error("AI tick failed for {} ({}), disabling its brain",
                        mob.getUUID(), mob.getType(), e);
            }
            brain.stopActiveGoal();
        }
    }
}
