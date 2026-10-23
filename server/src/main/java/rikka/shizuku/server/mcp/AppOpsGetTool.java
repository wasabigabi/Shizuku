package rikka.shizuku.server.mcp;

import android.app.AppOpsManager;
import android.app.AppOpsManagerHidden;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.rikka.tools.refine.Refine;
import rikka.hidden.compat.AppOpsApis;

/**
 * Reads the AppOps modes currently set for a package.
 *
 * READ_ONLY: it changes nothing. It reveals which sensitive operations a given
 * app is allowed/denied, so it is still granted explicitly per agent.
 *
 * Args: {@code packageName}, optional {@code uid}, optional {@code userId}
 * (default 0).
 */
public final class AppOpsGetTool implements CapabilityTool {

    public static final String NAME = "appops.get";

    private static final McpTool DESCRIPTOR = new McpTool(
            NAME,
            "Read the AppOps modes set for a package.",
            DangerLevel.READ_ONLY,
            false);

    @NonNull
    @Override
    public McpTool descriptor() {
        return DESCRIPTOR;
    }

    @NonNull
    @Override
    public JsonElement execute(@NonNull JsonObject args) throws Exception {
        String packageName = AppOpsArgs.requirePackage(args);
        int userId = AppOpsArgs.userId(args);
        int uid = AppOpsArgs.resolveUid(args, packageName, userId);

        JsonArray ops = new JsonArray();
        // AppOpsApis returns the public PackageOps type whose nested type is
        // @hide and not nameable here, so iterate it raw and let refine view
        // each element as the hidden type that exposes getOps()/getMode().
        @SuppressWarnings({"rawtypes", "unchecked"})
        java.util.List result = AppOpsApis.getOpsForPackage(uid, packageName, null);
        if (result != null) {
            for (Object poPublic : result) {
                AppOpsManagerHidden.PackageOps po = Refine.unsafeCast(poPublic);
                if (po == null || po.getOps() == null) {
                    continue;
                }
                for (AppOpsManagerHidden.OpEntry e : po.getOps()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("op", AppOpsManagerHidden.opToName(e.getOp()));
                    o.addProperty("mode", AppOpsModes.name(e.getMode()));
                    ops.add(o);
                }
            }
        }

        JsonObject out = new JsonObject();
        out.addProperty("packageName", packageName);
        out.addProperty("uid", uid);
        out.add("ops", ops);
        return out;
    }
}
