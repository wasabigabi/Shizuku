package rikka.shizuku.server.mcp;

/**
 * Outcome of a gate authorization check for one (agent, tool) call.
 */
public enum McpDecision {

    /** Proceed without further interaction. */
    ALLOW,

    /** Refuse outright (unknown token, unknown tool, or tool not allowlisted). */
    DENY,

    /**
     * Permitted in principle, but the call must stop for an explicit human
     * confirmation before it runs. Always the outcome for a
     * {@link DangerLevel#SENSITIVE} tool.
     */
    REQUIRE_CONFIRMATION
}
