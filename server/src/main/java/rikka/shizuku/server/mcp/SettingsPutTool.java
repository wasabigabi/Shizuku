package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Writes a single Android setting.
 *
 * STATE_CHANGE: it alters device state, so unless the user has pre-trusted it
 * for the agent, every call stops for a confirmation in the manager.
 *
 * Args: {@code namespace} (secure|global|system), {@code key}, {@code value}
 * (string; null clears), optional {@code userId} (default 0).
 */
public final class SettingsPutTool implements CapabilityTool {

    public static final String NAME = "settings.put";

    private static final McpTool DESCRIPTOR = new McpTool(
            NAME,
            "Write one setting (secure/global/system). Changes device state.",
            DangerLevel.STATE_CHANGE,
            false);

    private final SettingsAccess access;

    public SettingsPutTool(@NonNull SettingsAccess access) {
        this.access = access;
    }

    @NonNull
    @Override
    public McpTool descriptor() {
        return DESCRIPTOR;
    }

    @NonNull
    @Override
    public JsonElement execute(@NonNull JsonObject args) throws Exception {
        String namespace = Settings.requireNamespace(args);
        String key = Settings.requireKey(args);
        int userId = Settings.userId(args);
        String value = args.has("value") && !args.get("value").isJsonNull()
                ? args.get("value").getAsString() : null;

        access.put(namespace, key, value, userId);

        JsonObject o = new JsonObject();
        o.addProperty("namespace", namespace);
        o.addProperty("key", key);
        o.addProperty("userId", userId);
        o.addProperty("ok", true);
        return o;
    }
}
