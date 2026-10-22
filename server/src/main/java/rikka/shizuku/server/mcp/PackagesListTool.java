package rikka.shizuku.server.mcp;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.UserManagerApis;

/**
 * The first capability tool: lists installed packages.
 *
 * Read-only and touches no secrets, so it is {@link DangerLevel#READ_ONLY} and
 * needs no per-call confirmation. It is the smallest useful end-to-end proof
 * that an agent can reach a real system API through the gate.
 *
 * Args (all optional):
 * <ul>
 *   <li>{@code userId} (int): a single user id, or -1 / absent for all users.</li>
 *   <li>{@code systemApps} (bool, default false): include system apps.</li>
 * </ul>
 */
public final class PackagesListTool implements CapabilityTool {

    public static final String NAME = "packages.list";

    private static final McpTool DESCRIPTOR = new McpTool(
            NAME,
            "List installed packages (package name, version, uid, enabled state).",
            DangerLevel.READ_ONLY,
            false);

    @NonNull
    @Override
    public McpTool descriptor() {
        return DESCRIPTOR;
    }

    @NonNull
    @Override
    public JsonElement execute(@NonNull JsonObject args) {
        int requestedUser = args.has("userId") && !args.get("userId").isJsonNull()
                ? args.get("userId").getAsInt() : -1;
        boolean includeSystem = args.has("systemApps")
                && args.get("systemApps").getAsBoolean();

        List<Integer> users = new ArrayList<>();
        if (requestedUser == -1) {
            users.addAll(UserManagerApis.getUserIdsNoThrow());
        } else {
            users.add(requestedUser);
        }

        JsonArray result = new JsonArray();
        for (int user : users) {
            for (PackageInfo pi : PackageManagerApis.getInstalledPackagesNoThrow(0, user)) {
                if (pi == null || pi.applicationInfo == null) {
                    continue;
                }
                boolean isSystem = (pi.applicationInfo.flags
                        & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                if (isSystem && !includeSystem) {
                    continue;
                }
                JsonObject o = new JsonObject();
                o.addProperty("packageName", pi.packageName);
                o.addProperty("versionName", pi.versionName);
                o.addProperty("uid", pi.applicationInfo.uid);
                o.addProperty("userId", user);
                o.addProperty("enabled", pi.applicationInfo.enabled);
                o.addProperty("system", isSystem);
                result.add(o);
            }
        }
        return result;
    }
}
