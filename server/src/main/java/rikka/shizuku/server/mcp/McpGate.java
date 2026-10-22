package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

/**
 * The single enforcement point for the bridge.
 *
 * Every tool call an agent attempts goes through {@link #authorize}, and every
 * outcome is written to the {@link McpAuditLog}. No capability may ever call a
 * privileged API without first getting an {@link McpDecision#ALLOW} here (or an
 * {@link McpDecision#REQUIRE_CONFIRMATION} that a human then confirms via
 * {@link #confirm}). This class holds no privileged power itself; it only says
 * yes or no, and records why.
 *
 * Decision logic, in order:
 * <ol>
 *   <li>unknown or revoked token -> DENY</li>
 *   <li>unknown tool -> DENY</li>
 *   <li>tool not on the agent's allowlist -> DENY</li>
 *   <li>otherwise: SENSITIVE always -> REQUIRE_CONFIRMATION;
 *       READ_ONLY -> ALLOW; STATE_CHANGE -> ALLOW only if the user pre-trusted
 *       it for this agent and the tool does not opt into confirm-by-default,
 *       else REQUIRE_CONFIRMATION.</li>
 * </ol>
 * A SENSITIVE tool can never be pre-trusted away: that is the invariant that
 * keeps secret-reading and irreversible actions behind a fresh human decision.
 */
public final class McpGate {

    private final McpToolRegistry registry;
    private final McpPolicyManager policy;
    private final McpAuditLog audit;

    public McpGate(@NonNull McpToolRegistry registry,
                   @NonNull McpPolicyManager policy,
                   @NonNull McpAuditLog audit) {
        this.registry = registry;
        this.policy = policy;
        this.audit = audit;
    }

    /** Result of an authorization check, carrying enough to confirm later. */
    public static final class Result {
        public final McpDecision decision;
        public final String reason;
        public final String agentId;
        public final String agentLabel;

        Result(McpDecision decision, String reason, String agentId, String agentLabel) {
            this.decision = decision;
            this.reason = reason;
            this.agentId = agentId;
            this.agentLabel = agentLabel;
        }
    }

    /**
     * Decides whether {@code rawToken} may call {@code toolName}, records the
     * decision, and returns it. {@code argsSummary} is a short, caller-supplied
     * description of the arguments for the audit trail (never the full payload).
     */
    @NonNull
    public Result authorize(@NonNull String rawToken, @NonNull String toolName,
                            String argsSummary) {
        McpPolicy.Agent agent = policy.resolveToken(rawToken);
        if (agent == null) {
            audit.record(McpAuditLog.Event.DENIED, null, null, toolName, argsSummary,
                    "unknown or revoked token");
            return new Result(McpDecision.DENY, "unknown or revoked token", null, null);
        }

        McpTool tool = registry.find(toolName);
        if (tool == null) {
            audit.record(McpAuditLog.Event.DENIED, agent.id, agent.label, toolName, argsSummary,
                    "unknown tool");
            return new Result(McpDecision.DENY, "unknown tool", agent.id, agent.label);
        }

        if (!agent.allowedTools.contains(toolName)) {
            audit.record(McpAuditLog.Event.DENIED, agent.id, agent.label, toolName, argsSummary,
                    "tool not allowed for agent");
            return new Result(McpDecision.DENY, "tool not allowed for agent", agent.id, agent.label);
        }

        McpDecision decision = decide(tool, agent);
        if (decision == McpDecision.ALLOW) {
            audit.record(McpAuditLog.Event.AUTHORIZED, agent.id, agent.label, toolName, argsSummary, null);
        } else {
            audit.record(McpAuditLog.Event.CONFIRM_REQUIRED, agent.id, agent.label, toolName, argsSummary,
                    tool.getDangerLevel().name());
        }
        return new Result(decision, decision.name(), agent.id, agent.label);
    }

    private McpDecision decide(McpTool tool, McpPolicy.Agent agent) {
        switch (tool.getDangerLevel()) {
            case SENSITIVE:
                return McpDecision.REQUIRE_CONFIRMATION;
            case READ_ONLY:
                return tool.isConfirmByDefault()
                        ? McpDecision.REQUIRE_CONFIRMATION : McpDecision.ALLOW;
            case STATE_CHANGE:
            default:
                boolean preTrusted = agent.trustedTools.contains(tool.getName())
                        && !tool.isConfirmByDefault();
                return preTrusted ? McpDecision.ALLOW : McpDecision.REQUIRE_CONFIRMATION;
        }
    }

    /**
     * Records the resolution of a confirmation the user was shown. The gate
     * does not run the tool; it only attests that the human said yes or no.
     */
    public void confirm(@NonNull String agentId, @NonNull String agentLabel,
                        @NonNull String toolName, String argsSummary, boolean approved) {
        audit.record(approved ? McpAuditLog.Event.CONFIRMED : McpAuditLog.Event.REJECTED,
                agentId, agentLabel, toolName, argsSummary, null);
    }

    /** Records the outcome of actually running a tool, for the audit trail. */
    public void recordOutcome(@NonNull String agentId, @NonNull String agentLabel,
                              @NonNull String toolName, String argsSummary,
                              boolean success, String detail) {
        audit.record(success ? McpAuditLog.Event.EXECUTED : McpAuditLog.Event.FAILED,
                agentId, agentLabel, toolName, argsSummary, detail);
    }
}
