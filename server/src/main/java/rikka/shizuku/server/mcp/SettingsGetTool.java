package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

/**
 * Reads a single Android setting.
 *
 * READ_ONLY: it changes nothing. It can surface values the user may consider
 * sensitive, so it is still granted explicitly per agent via the allowlist.
 *
 * Args: {@code namespace} (secure|global|system), {@code key}, optional
 * {@code userId} (default 0).
 */
public final class SettingsGetTool implements CapabilityTool {

    public static final String NAME = "settings.get";

    static final List<String> NAMESPACES = Arrays.asList("secure", "global", "system");

    private static final McpTool DESCRIPTOR = new McpTool(
            NAME,
            "Read one setting (secure/global/system). May expose sensitive values.",
            DangerLevel.READ_ONLY,
            false);

    private final SettingsAccess access;

    public SettingsGetTool(@NonNull SettingsAccess access) {
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

        String value = access.get(namespace, key, userId);
        JsonObject o = new JsonObject();
        o.addProperty("namespace", namespace);
        o.addProperty("key", key);
        o.addProperty("userId", userId);
        if (value == null) {
            o.add("value", JsonNull.INSTANCE);
        } else {
            o.addProperty("value", value);
        }
        return o;
    }
}
