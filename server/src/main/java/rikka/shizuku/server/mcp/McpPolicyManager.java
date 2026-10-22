package rikka.shizuku.server.mcp;

import android.util.AtomicFile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rikka.shizuku.server.util.Logger;

/**
 * Owns the paired-agent policy: pairing, revocation, allowlist edits, and
 * resolving an incoming token to an agent.
 *
 * Persistence matches {@link rikka.shizuku.server.ShizukuConfigManager}: a
 * Gson-serialized root written atomically to the shizuku data dir. Writes are
 * synchronous here (the policy changes rarely, only on explicit user action),
 * which keeps the on-disk state consistent with what the user just approved.
 */
public final class McpPolicyManager {

    private static final Logger LOGGER = new Logger("ShizukuMcpPolicy");

    private static final File FILE = new File("/data/local/tmp/shizuku/mcp-policy.json");

    private final AtomicFile atomicFile;
    private final Gson gson;
    private final McpPolicy policy;

    public McpPolicyManager() {
        this(FILE);
    }

    /** Visible for testing with a temp file. */
    McpPolicyManager(@NonNull File file) {
        this.atomicFile = new AtomicFile(file);
        this.gson = new GsonBuilder().setVersion(McpPolicy.LATEST_VERSION).create();
        this.policy = load(atomicFile, gson);
    }

    private static McpPolicy load(AtomicFile atomicFile, Gson gson) {
        FileInputStream stream;
        try {
            stream = atomicFile.openRead();
        } catch (FileNotFoundException e) {
            LOGGER.i("no existing mcp policy; starting empty");
            return new McpPolicy();
        }
        McpPolicy loaded = null;
        try {
            loaded = gson.fromJson(new InputStreamReader(stream), McpPolicy.class);
        } catch (Throwable tr) {
            LOGGER.w(tr, "load mcp policy");
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.w("failed to close: " + e);
            }
        }
        if (loaded == null) {
            loaded = new McpPolicy();
        }
        if (loaded.agents == null) {
            loaded.agents = new ArrayList<>();
        }
        return loaded;
    }

    private void writeLocked() {
        FileOutputStream stream;
        try {
            stream = atomicFile.startWrite();
        } catch (IOException e) {
            LOGGER.w("failed to start mcp policy write: " + e);
            return;
        }
        try {
            stream.write(gson.toJson(policy).getBytes());
            atomicFile.finishWrite(stream);
            LOGGER.v("mcp policy saved");
        } catch (Throwable tr) {
            LOGGER.w(tr, "can't save mcp policy, restoring backup");
            atomicFile.failWrite(stream);
        }
    }

    /**
     * Result of pairing. The raw token lives only in this object and is shown
     * to the user once; it is never persisted.
     */
    public static final class Paired {
        public final String agentId;
        public final String rawToken;

        Paired(String agentId, String rawToken) {
            this.agentId = agentId;
            this.rawToken = rawToken;
        }
    }

    /** Creates a new agent and returns its one-time raw token. */
    @NonNull
    public synchronized Paired pair(@NonNull String label) {
        String token = Tokens.newToken();
        String id = UUID.randomUUID().toString().substring(0, 8);
        McpPolicy.Agent agent = new McpPolicy.Agent(
                id, label, Tokens.hash(token), System.currentTimeMillis());
        policy.agents.add(agent);
        writeLocked();
        LOGGER.i("paired agent %s (%s)", id, label);
        return new Paired(id, token);
    }

    public synchronized boolean revoke(@NonNull String agentId) {
        McpPolicy.Agent agent = findByIdLocked(agentId);
        if (agent == null || agent.revoked) {
            return false;
        }
        agent.revoked = true;
        writeLocked();
        LOGGER.i("revoked agent %s", agentId);
        return true;
    }

    /** Sets the full allowlist for an agent, replacing any previous set. */
    public synchronized boolean setAllowedTools(@NonNull String agentId, @NonNull List<String> tools) {
        McpPolicy.Agent agent = findByIdLocked(agentId);
        if (agent == null) {
            return false;
        }
        agent.allowedTools = new ArrayList<>(tools);
        // A tool that is no longer allowed cannot remain pre-trusted.
        agent.trustedTools.retainAll(agent.allowedTools);
        writeLocked();
        return true;
    }

    /**
     * Marks a tool as pre-trusted for an agent (no per-call confirmation for
     * {@link DangerLevel#STATE_CHANGE}). The tool must already be allowed.
     */
    public synchronized boolean setTrusted(@NonNull String agentId, @NonNull String tool, boolean trusted) {
        McpPolicy.Agent agent = findByIdLocked(agentId);
        if (agent == null || !agent.allowedTools.contains(tool)) {
            return false;
        }
        if (trusted) {
            if (!agent.trustedTools.contains(tool)) {
                agent.trustedTools.add(tool);
            }
        } else {
            agent.trustedTools.remove(tool);
        }
        writeLocked();
        return true;
    }

    /** Resolves an incoming raw token to a live (non-revoked) agent, or null. */
    @Nullable
    public synchronized McpPolicy.Agent resolveToken(@NonNull String rawToken) {
        String hash = Tokens.hash(rawToken);
        for (McpPolicy.Agent agent : policy.agents) {
            if (!agent.revoked && agent.tokenHash != null
                    && Tokens.hashEquals(agent.tokenHash, hash)) {
                return agent;
            }
        }
        return null;
    }

    @Nullable
    private McpPolicy.Agent findByIdLocked(@NonNull String agentId) {
        for (McpPolicy.Agent agent : policy.agents) {
            if (agentId.equals(agent.id)) {
                return agent;
            }
        }
        return null;
    }

    /** Snapshot of all agents, for the manager UI. Tokens are never exposed. */
    @NonNull
    public synchronized List<McpPolicy.Agent> listAgents() {
        return new ArrayList<>(policy.agents);
    }

    public synchronized boolean isTransportEnabled() {
        return policy.transportEnabled;
    }

    public synchronized int getTransportPort() {
        return policy.transportPort;
    }

    public synchronized void setTransport(boolean enabled, int port) {
        policy.transportEnabled = enabled;
        if (port > 0) {
            policy.transportPort = port;
        }
        writeLocked();
    }
}
