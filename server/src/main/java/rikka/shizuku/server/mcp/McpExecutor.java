package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs capability tools for agents, strictly behind the gate.
 *
 * Registering a capability also publishes its descriptor into the shared
 * {@link McpToolRegistry}, so the gate and the manager UI see exactly the tools
 * that can actually run. Every {@link #run} goes through
 * {@link McpGate#authorize}; a tool body executes only on
 * {@link McpDecision#ALLOW}, and its outcome is recorded.
 */
public final class McpExecutor {

    private final McpToolRegistry registry;
    private final McpPolicyManager policy;
    private final McpGate gate;
    private final Map<String, CapabilityTool> tools = new LinkedHashMap<>();

    public McpExecutor(@NonNull McpToolRegistry registry,
                       @NonNull McpPolicyManager policy,
                       @NonNull McpGate gate) {
        this.registry = registry;
        this.policy = policy;
        this.gate = gate;
    }

    /** Registers a capability and publishes its descriptor to the registry. */
    public synchronized void register(@NonNull CapabilityTool tool) {
        registry.register(tool.descriptor());
        tools.put(tool.descriptor().getName(), tool);
    }

    /**
     * Authorizes and (if allowed) runs {@code toolName} for the holder of
     * {@code rawToken}. Always returns a JSON object string:
     * {@code {"result": ...}} on success, or {@code {"error": "...",
     * "code": "..."}} otherwise. Never throws.
     */
    @NonNull
    public String run(@NonNull String rawToken, @NonNull String toolName, String argsJson) {
        final JsonObject args;
        try {
            args = parseArgs(argsJson);
        } catch (Throwable tr) {
            return error("invalid_args", "arguments must be a JSON object");
        }

        String summary = toolName + (argsJson != null ? " " + argsJson : "");
        McpGate.Result decision = gate.authorize(rawToken, toolName, summary);

        switch (decision.decision) {
            case DENY:
                return error("denied", decision.reason);
            case REQUIRE_CONFIRMATION:
                // No agent-facing confirmation channel exists yet; it arrives
                // with the first SENSITIVE/confirming tool. Until then such a
                // call cannot proceed.
                return error("confirmation_required",
                        "this tool requires a confirmation that is not available yet");
            case ALLOW:
            default:
                break;
        }

        CapabilityTool tool = tools.get(toolName);
        if (tool == null) {
            // Registry and executor are populated together, so this is unexpected.
            return error("unknown_tool", "no executor for " + toolName);
        }

        try {
            JsonElement out = tool.execute(args);
            gate.recordOutcome(decision.agentId, decision.agentLabel, toolName, summary, true, null);
            JsonObject o = new JsonObject();
            o.add("result", out);
            return o.toString();
        } catch (Throwable tr) {
            String detail = tr.getClass().getSimpleName() + ": " + tr.getMessage();
            gate.recordOutcome(decision.agentId, decision.agentLabel, toolName, summary, false, detail);
            return error("exception", detail);
        }
    }

    /**
     * Lists the tools the holder of {@code rawToken} is allowed to call, as a
     * JSON object string {@code {"result": [ ... ]}}. An unknown token yields
     * an empty list (it is not told which tools exist).
     */
    @NonNull
    public String listForToken(@NonNull String rawToken) {
        JsonArray arr = new JsonArray();
        McpPolicy.Agent agent = policy.resolveToken(rawToken);
        if (agent != null) {
            for (McpTool t : registry.list()) {
                if (agent.allowedTools.contains(t.getName())) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", t.getName());
                    o.addProperty("description", t.getDescription());
                    o.addProperty("dangerLevel", t.getDangerLevel().name());
                    arr.add(o);
                }
            }
        }
        JsonObject o = new JsonObject();
        o.add("result", arr);
        return o.toString();
    }

    private static JsonObject parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isEmpty()) {
            return new JsonObject();
        }
        return JsonParser.parseString(argsJson).getAsJsonObject();
    }

    private static String error(String code, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        o.addProperty("code", code);
        return o.toString();
    }
}
