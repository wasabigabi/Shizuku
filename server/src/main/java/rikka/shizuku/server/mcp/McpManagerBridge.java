package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches manager-app requests about the MCP policy to the underlying
 * managers, speaking JSON in and JSON out.
 *
 * This is the server-side endpoint the manager UI talks to over the Shizuku
 * binder (see {@code ShizukuService.onTransact}). It is deliberately a thin,
 * side-effect-free-to-parse dispatcher so it can be unit-tested on a desktop
 * JVM without a device or a binder.
 *
 * Caller authentication (manager-only) is enforced by the transport before a
 * request ever reaches here; this class assumes the caller is the manager.
 */
public final class McpManagerBridge {

    private final McpToolRegistry registry;
    private final McpPolicyManager policy;
    private final McpAuditLog audit;

    public McpManagerBridge(@NonNull McpToolRegistry registry,
                            @NonNull McpPolicyManager policy,
                            @NonNull McpAuditLog audit) {
        this.registry = registry;
        this.policy = policy;
        this.audit = audit;
    }

    /**
     * Handles one request. {@code argsJson} may be null/empty for methods that
     * take no arguments. Always returns a JSON object string; on failure it is
     * {@code {"error": "..."}} rather than a thrown exception, so the transport
     * can relay it cleanly.
     */
    @NonNull
    public String handle(@NonNull String method, String argsJson) {
        try {
            JsonObject args = parseArgs(argsJson);
            switch (method) {
                case "listTools":
                    return ok(listTools());
                case "listAgents":
                    return ok(listAgents());
                case "recentAudit":
                    return ok(recentAudit());
                case "pair":
                    return pair(args);
                case "revoke":
                    return boolResult(policy.revoke(req(args, "agentId")));
                case "setAllowedTools":
                    return setAllowedTools(args);
                case "setTrusted":
                    return boolResult(policy.setTrusted(
                            req(args, "agentId"), req(args, "tool"),
                            args.get("trusted").getAsBoolean()));
                default:
                    return error("unknown method: " + method);
            }
        } catch (Throwable tr) {
            return error(tr.getClass().getSimpleName() + ": " + tr.getMessage());
        }
    }

    private String pair(JsonObject args) {
        McpPolicyManager.Paired p = policy.pair(req(args, "label"));
        JsonObject o = new JsonObject();
        o.addProperty("agentId", p.agentId);
        o.addProperty("rawToken", p.rawToken);
        return wrap(o);
    }

    private String setAllowedTools(JsonObject args) {
        List<String> tools = new ArrayList<>();
        JsonArray arr = args.getAsJsonArray("tools");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                tools.add(arr.get(i).getAsString());
            }
        }
        return boolResult(policy.setAllowedTools(req(args, "agentId"), tools));
    }

    private JsonArray listTools() {
        JsonArray arr = new JsonArray();
        for (McpTool tool : registry.list()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", tool.getName());
            o.addProperty("description", tool.getDescription());
            o.addProperty("dangerLevel", tool.getDangerLevel().name());
            o.addProperty("confirmByDefault", tool.isConfirmByDefault());
            arr.add(o);
        }
        return arr;
    }

    private JsonArray listAgents() {
        JsonArray arr = new JsonArray();
        for (McpPolicy.Agent agent : policy.listAgents()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", agent.id);
            o.addProperty("label", agent.label);
            o.addProperty("createdAt", agent.createdAt);
            o.addProperty("revoked", agent.revoked);
            o.add("allowedTools", toArray(agent.allowedTools));
            o.add("trustedTools", toArray(agent.trustedTools));
            arr.add(o);
        }
        return arr;
    }

    private JsonArray recentAudit() {
        JsonArray arr = new JsonArray();
        for (McpAuditLog.Entry e : audit.recent()) {
            JsonObject o = new JsonObject();
            o.addProperty("timestamp", e.timestamp);
            o.addProperty("agentId", e.agentId);
            o.addProperty("agentLabel", e.agentLabel);
            o.addProperty("tool", e.tool);
            o.addProperty("argsSummary", e.argsSummary);
            o.addProperty("event", e.event);
            o.addProperty("detail", e.detail);
            arr.add(o);
        }
        return arr;
    }

    // --- helpers ---

    private static JsonObject parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isEmpty()) {
            return new JsonObject();
        }
        return JsonParser.parseString(argsJson).getAsJsonObject();
    }

    private static String req(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing argument: " + key);
        }
        return args.get(key).getAsString();
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray arr = new JsonArray();
        if (values != null) {
            for (String v : values) {
                arr.add(v);
            }
        }
        return arr;
    }

    private static String ok(JsonArray data) {
        JsonObject o = new JsonObject();
        o.add("data", data);
        return o.toString();
    }

    private static String wrap(JsonObject data) {
        JsonObject o = new JsonObject();
        o.add("data", data);
        return o.toString();
    }

    private static String boolResult(boolean value) {
        JsonObject data = new JsonObject();
        data.addProperty("ok", value);
        return wrap(data);
    }

    private static String error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o.toString();
    }
}
