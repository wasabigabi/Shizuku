package rikka.shizuku.server.mcp;

import com.google.gson.JsonObject;

import rikka.hidden.compat.PackageManagerApis;

/**
 * Argument parsing shared by the AppOps tools, including resolving a package
 * name to its uid when the caller does not supply one.
 */
final class AppOpsArgs {

    private AppOpsArgs() {
    }

    static String requirePackage(JsonObject args) {
        if (!args.has("packageName") || args.get("packageName").isJsonNull()) {
            throw new IllegalArgumentException("missing argument: packageName");
        }
        String pkg = args.get("packageName").getAsString();
        if (pkg.isEmpty()) {
            throw new IllegalArgumentException("packageName must not be empty");
        }
        return pkg;
    }

    static int userId(JsonObject args) {
        return args.has("userId") && !args.get("userId").isJsonNull()
                ? args.get("userId").getAsInt() : 0;
    }

    static int resolveUid(JsonObject args, String packageName, int userId) throws Exception {
        if (args.has("uid") && !args.get("uid").isJsonNull()) {
            return args.get("uid").getAsInt();
        }
        int uid = PackageManagerApis.getPackageUid(packageName, 0, userId);
        if (uid < 0) {
            throw new IllegalArgumentException("package not found: " + packageName + " (user " + userId + ")");
        }
        return uid;
    }
}
