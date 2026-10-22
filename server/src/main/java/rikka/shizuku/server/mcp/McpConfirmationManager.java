package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Holds confirmations that are waiting for a human decision in the manager.
 *
 * When the gate says a call needs confirmation, the executor parks it here and
 * blocks the calling (transport) thread until the user approves or rejects it
 * in the manager, or it times out. The manager lists pending requests and
 * resolves them. This is the hinge that lets a SENSITIVE or non-trusted
 * STATE_CHANGE tool run only after an explicit, per-call human decision.
 *
 * Deny-by-default: a timeout, or no decision, means rejected.
 */
public final class McpConfirmationManager {

    /** Default time a parked call waits for a decision before being rejected. */
    public static final long DEFAULT_TIMEOUT_MS = 120 * 1000;

    /** Cap on simultaneously parked requests, to bound resource use. */
    private static final int MAX_PENDING = 16;

    public static final class Pending {
        public final String id;
        public final String agentId;
        public final String agentLabel;
        public final String tool;
        public final String argsSummary;
        public final long createdAt;

        final CountDownLatch latch = new CountDownLatch(1);
        volatile boolean approved = false;

        Pending(String id, String agentId, String agentLabel, String tool, String argsSummary) {
            this.id = id;
            this.agentId = agentId;
            this.agentLabel = agentLabel;
            this.tool = tool;
            this.argsSummary = argsSummary;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final long timeoutMs;

    public McpConfirmationManager() {
        this(DEFAULT_TIMEOUT_MS);
    }

    public McpConfirmationManager(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Parks a call and blocks until it is resolved or times out. Returns true
     * only if a human explicitly approved it. Must be called off the main
     * thread (it blocks).
     */
    public boolean await(@NonNull String agentId, @NonNull String agentLabel,
                         @NonNull String tool, String argsSummary) {
        if (pending.size() >= MAX_PENDING) {
            return false;
        }
        String id = UUID.randomUUID().toString();
        Pending p = new Pending(id, agentId, agentLabel, tool, argsSummary);
        pending.put(id, p);
        try {
            boolean signalled = p.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return signalled && p.approved;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pending.remove(id);
        }
    }

    /** Resolves a pending request. Returns false if it is unknown/already gone. */
    public boolean resolve(@NonNull String id, boolean approved) {
        Pending p = pending.get(id);
        if (p == null) {
            return false;
        }
        p.approved = approved;
        p.latch.countDown();
        return true;
    }

    /** Snapshot of currently parked requests, oldest first. */
    @NonNull
    public List<Pending> list() {
        List<Pending> out = new ArrayList<>(pending.values());
        out.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
        return out;
    }
}
