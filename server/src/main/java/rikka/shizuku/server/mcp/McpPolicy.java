package rikka.shizuku.server.mcp;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted policy model (serialized to JSON via Gson).
 *
 * Mirrors the shape of {@link rikka.shizuku.server.ShizukuConfig}: a versioned
 * root holding a flat list of entries. Here each entry is a paired agent and
 * the exact tools it may call.
 */
public class McpPolicy {

    public static final int LATEST_VERSION = 1;

    @SerializedName("version")
    public int version = LATEST_VERSION;

    @SerializedName("agents")
    public List<Agent> agents = new ArrayList<>();

    /**
     * One paired agent.
     *
     * The raw pairing token is never stored. Only {@link #tokenHash}
     * (SHA-256, hex) is kept, so a leak of the policy file does not leak a
     * usable credential. The raw token is shown to the user exactly once, at
     * pairing time.
     */
    public static class Agent {

        @SerializedName("id")
        public String id;

        @SerializedName("label")
        public String label;

        @SerializedName("tokenHash")
        public String tokenHash;

        @SerializedName("createdAt")
        public long createdAt;

        @SerializedName("revoked")
        public boolean revoked;

        /** Tools this agent is permitted to call at all. */
        @SerializedName("allowedTools")
        public List<String> allowedTools = new ArrayList<>();

        /**
         * Tools the user has explicitly pre-trusted for this agent, so a
         * {@link DangerLevel#STATE_CHANGE} tool runs without a per-call
         * confirmation. Has no effect on {@link DangerLevel#SENSITIVE} tools,
         * which always confirm.
         */
        @SerializedName("trustedTools")
        public List<String> trustedTools = new ArrayList<>();

        public Agent() {
        }

        public Agent(String id, String label, String tokenHash, long createdAt) {
            this.id = id;
            this.label = label;
            this.tokenHash = tokenHash;
            this.createdAt = createdAt;
        }
    }
}
