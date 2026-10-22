package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * The set of tools the bridge knows about.
 *
 * The gate can only authorize a tool that is registered here, so this registry
 * is the complete, auditable list of everything an agent could ever be allowed
 * to do. It ships empty: capability modules register descriptors in a later
 * step, each a deliberate, reviewable addition.
 */
public final class McpToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    /**
     * Registers a tool. Rejects a duplicate name so two capability modules can
     * never silently shadow each other.
     */
    public synchronized void register(@NonNull McpTool tool) {
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("duplicate tool name: " + tool.getName());
        }
        tools.put(tool.getName(), tool);
    }

    @Nullable
    public synchronized McpTool find(@NonNull String name) {
        return tools.get(name);
    }

    @NonNull
    public synchronized List<McpTool> list() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    public synchronized boolean isEmpty() {
        return tools.isEmpty();
    }
}
