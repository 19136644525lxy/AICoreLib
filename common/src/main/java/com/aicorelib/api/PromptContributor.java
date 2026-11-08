package com.aicorelib.api;

import java.util.List;

/**
 * Lets an addon append lines to the system prompt of every agent (or of the agents it cares
 * about).
 *
 * <p>Kept deliberately dumb: contributors append plain text, the library owns the ordering
 * and the token budget. A contributor that wants to decide per-agent should just return
 * early — {@code ctx} is the same context the goals get.</p>
 */
public interface PromptContributor {
    void contribute(AgentContext ctx, List<String> lines);

    /** Lower runs earlier. The library's own persona block is 0, the action list is 100. */
    default int priority() {
        return 1000;
    }
}
