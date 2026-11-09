package com.aicorelib.agent;

/** Why {@link AgentService#tryInject} did or did not attach an AI to a mob. */
public enum InjectionResult {
    /** The mob is now an agent. */
    OK,
    /** It already was one; nothing changed. */
    ALREADY,
    /** The server-wide agent limit is full. */
    LIMIT_TOTAL,
    /** This owner already has as many agents as the config allows. */
    LIMIT_OWNER,
    /** The entity type is blacklisted, or an allowlist exists and does not contain it. */
    DENIED_TYPE
}
