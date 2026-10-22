package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

/**
 * Static description of a single tool an agent may invoke.
 *
 * At this stage the bridge ships zero capability tools on purpose; capability
 * modules register their descriptors with {@link McpToolRegistry} in a later
 * step. A descriptor carries no behaviour, only identity and risk.
 */
public final class McpTool {

    private final String name;
    private final String description;
    private final DangerLevel dangerLevel;
    private final boolean confirmByDefault;

    public McpTool(@NonNull String name, @NonNull String description,
                   @NonNull DangerLevel dangerLevel, boolean confirmByDefault) {
        this.name = name;
        this.description = description;
        this.dangerLevel = dangerLevel;
        this.confirmByDefault = confirmByDefault;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @NonNull
    public DangerLevel getDangerLevel() {
        return dangerLevel;
    }

    /**
     * Whether the tool asks for a human confirmation on every call even when
     * the agent has it on its allowlist. Independent of {@link DangerLevel}:
     * a {@link DangerLevel#SENSITIVE} tool always confirms regardless, but a
     * {@link DangerLevel#STATE_CHANGE} tool may opt into it here too.
     */
    public boolean isConfirmByDefault() {
        return confirmByDefault;
    }
}
