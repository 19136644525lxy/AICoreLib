package com.aicorelib.llm;

import com.aicorelib.AICoreLib;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cheap, in-memory guard rails for the remote brain.
 *
 * <p>Three separate limits, because they fail in different ways: the rate limit stops a
 * stampede when fifty agents see a zombie at once; the hourly token budget stops a slow bleed
 * that only shows up on the bill; the circuit breaker stops the library from hammering a
 * provider that is down (and from paying for every retry).</p>
 *
 * <p>Counters live in memory on purpose — restarting the server clears them, which is exactly
 * what an admin wants after fighting a runaway loop.</p>
 */
public final class LlmBudget {
    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 3_600_000L;
    private static final int FAILURES_TO_TRIP = 3;
    private static final long TRIP_DURATION_MS = 60_000L;

    private static final Deque<Long> REQUEST_TIMES = new ArrayDeque<>();
    private static final Map<UUID, Window> AGENT_WINDOWS = new ConcurrentHashMap<>();
    private static final Window GLOBAL = new Window();

    private static int consecutiveFailures;
    private static long openUntilMs;

    private LlmBudget() {
    }

    /** Whether a request may be started right now. Does not reserve anything. */
    public static boolean allowRequest(UUID agent) {
        LlmConfig config = LlmConfig.get();
        long now = System.currentTimeMillis();

        if (now < openUntilMs) {
            return false;
        }
        synchronized (REQUEST_TIMES) {
            while (!REQUEST_TIMES.isEmpty() && now - REQUEST_TIMES.peekFirst() > MINUTE_MS) {
                REQUEST_TIMES.pollFirst();
            }
            if (REQUEST_TIMES.size() >= Math.max(1, config.requestsPerMinute())) {
                return false;
            }
        }
        if (config.tokensPerHourAgent() > 0 && windowFor(agent).used(now) >= config.tokensPerHourAgent()) {
            return false;
        }
        return config.tokensPerHourGlobal() <= 0 || GLOBAL.used(now) < config.tokensPerHourGlobal();
    }

    /** Records that a request was sent, before the answer arrives. */
    public static void noteRequest() {
        synchronized (REQUEST_TIMES) {
            REQUEST_TIMES.addLast(System.currentTimeMillis());
        }
    }

    public static void recordSuccess(UUID agent, int tokens) {
        consecutiveFailures = 0;
        long now = System.currentTimeMillis();
        if (tokens > 0) {
            windowFor(agent).add(now, tokens);
            GLOBAL.add(now, tokens);
        }
    }

    public static void recordFailure(UUID agent, String reason) {
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURES_TO_TRIP && System.currentTimeMillis() >= openUntilMs) {
            openUntilMs = System.currentTimeMillis() + TRIP_DURATION_MS;
            AICoreLib.LOGGER.warn("LLM circuit opened for {}s after {} failures (last: {})",
                    TRIP_DURATION_MS / 1000, consecutiveFailures, reason);
        }
    }

    public static boolean circuitOpen() {
        return System.currentTimeMillis() < openUntilMs;
    }

    public static long circuitRemainingMs() {
        return Math.max(0L, openUntilMs - System.currentTimeMillis());
    }

    /** Human readable dump for {@code /aicorelib llm status}. */
    public static String status() {
        long now = System.currentTimeMillis();
        int requestsLastMinute;
        synchronized (REQUEST_TIMES) {
            requestsLastMinute = (int) REQUEST_TIMES.stream().filter(t -> now - t <= MINUTE_MS).count();
        }
        return "requests/min " + requestsLastMinute + "/" + LlmConfig.get().requestsPerMinute()
                + ", tokens/h global " + GLOBAL.used(now) + "/" + LlmConfig.get().tokensPerHourGlobal()
                + ", circuit " + (circuitOpen() ? "OPEN (" + circuitRemainingMs() / 1000 + "s)" : "closed");
    }

    /** Called when the config is reloaded, so a fixed key is not stuck behind an old trip. */
    public static void reset() {
        synchronized (REQUEST_TIMES) {
            REQUEST_TIMES.clear();
        }
        AGENT_WINDOWS.clear();
        GLOBAL.clear();
        consecutiveFailures = 0;
        openUntilMs = 0L;
    }

    private static Window windowFor(UUID agent) {
        return AGENT_WINDOWS.computeIfAbsent(agent, id -> new Window());
    }

    private static final class Window {
        private long start;
        private int tokens;

        synchronized int used(long now) {
            roll(now);
            return tokens;
        }

        synchronized void add(long now, int amount) {
            roll(now);
            tokens += amount;
        }

        synchronized void clear() {
            start = 0L;
            tokens = 0;
        }

        private void roll(long now) {
            if (now - start > HOUR_MS) {
                start = now;
                tokens = 0;
            }
        }
    }
}
