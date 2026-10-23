package rikka.shizuku.server.mcp;

import android.app.AppOpsManagerHidden;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import rikka.hidden.compat.AppOpsApis;

/**
 * Sets the AppOps mode for one operation on a package.
 *
 * SENSITIVE: granting or denying an operation (camera, microphone, draw-over,
 * etc.) is high-impact, so every call stops for a human confirmation in the
 * manager and can never be pre-trusted away.
 *
 * Args: {@code packageName}, {@code op} (op string or short name, or numeric
 * code), {@code mode} (allow|ignore|error|default|foreground, or numeric),
 * optional {@code uid}, optional {@code userId} (default 0).
 */
public final class AppOpsSetTool implements CapabilityTool {

    public static final String NAME = "appops.set";

    private static final McpTool DESCRIPTOR = new McpTool(
            NAME,
            "Set an AppOps mode for a package (grant/deny an operation).",
            DangerLevel.SENSITIVE,
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

        if (!args.has("op") || args.get("op").isJsonNull()) {
            throw new IllegalArgumentException("missing argument: op");
        }
        if (!args.has("mode") || args.get("mode").isJsonNull()) {
            throw new IllegalArgumentException("missing argument: mode");
        }
        int code = resolveOp(args.get("op").getAsString());
        int mode = AppOpsModes.fromString(args.get("mode").getAsString());

        AppOpsApis.setMode(code, uid, packageName, mode);

        JsonObject out = new JsonObject();
        out.addProperty("packageName", packageName);
        out.addProperty("uid", uid);
        out.addProperty("op", AppOpsManagerHidden.opToName(code));
        out.addProperty("mode", AppOpsModes.name(mode));
        out.addProperty("ok", true);
        return out;
    }

    private static int resolveOp(String op) {
        try {
            return AppOpsManagerHidden.strOpToOp(op);
        } catch (Throwable ignored) {
        }
        try {
            return AppOpsManagerHidden.strDebugOpToOp(op);
        } catch (Throwable ignored) {
        }
        try {
            return Integer.parseInt(op.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("unknown op: " + op);
        }
    }
}
