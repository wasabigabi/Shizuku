package rikka.shizuku.server.mcp;

/**
 * How much damage a tool can do if an agent is tricked into calling it.
 *
 * This is the single most important classification in the whole bridge: it
 * decides whether a call can ever be pre-approved, or must always stop for an
 * explicit human confirmation. See {@link McpGate} for the enforcement.
 */
public enum DangerLevel {

    /**
     * Reads non-sensitive state and changes nothing (e.g. list installed
     * packages, read a public setting). Safe to pre-approve per agent.
     */
    READ_ONLY,

    /**
     * Changes device state but touches no secrets (e.g. toggle a setting,
     * set an AppOp). May be pre-approved by the user, but defaults to
     * requiring confirmation.
     */
    STATE_CHANGE,

    /**
     * Reads secrets or performs an irreversible/high-impact action
     * (e.g. read notifications, which can expose 2FA codes; install a
     * package; inject input). Can NEVER be pre-approved away: every single
     * call requires a fresh human confirmation, by design.
     */
    SENSITIVE
}
