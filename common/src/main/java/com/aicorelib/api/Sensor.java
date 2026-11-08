package com.aicorelib.api;

/**
 * A perception pass over the world around one agent.
 *
 * <p>Implementations must be cheap and must not mutate the agent. They run every
 * {@code sensorIntervalTicks} (default 10) on the server thread. A sensor publishes
 * its result through {@link AgentContext#perception()} or {@link AgentContext#extras()}.</p>
 */
public interface Sensor {
    /** Called on the server thread, at most once per sensor interval. */
    void sample(AgentContext ctx);

    /**
     * Higher priority sensors run first, which lets one sensor build on another's output.
     */
    default int priority() {
        return 0;
    }
}
