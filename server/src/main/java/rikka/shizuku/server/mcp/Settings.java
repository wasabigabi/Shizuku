package rikka.shizuku.server.mcp;

import com.google.gson.JsonObject;

/**
 * Argument validation shared by the settings tools. Keeps the namespace
 * whitelist and key/user parsing in one place.
 */
final class Settings {

    private Settings() {
    }

    static String requireNamespace(JsonObject args) {
        String ns = requireString(args, "namespace");
        if (!SettingsGetTool.NAMESPACES.contains(ns)) {
            throw new IllegalArgumentException("namespace must be one of " + SettingsGetTool.NAMESPACES);
        }
        return ns;
    }

    static String requireKey(JsonObject args) {
        String key = requireString(args, "key");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        return key;
    }

    static int userId(JsonObject args) {
        return args.has("userId") && !args.get("userId").isJsonNull()
                ? args.get("userId").getAsInt() : 0;
    }

    private static String requireString(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("missing argument: " + name);
        }
        return args.get(name).getAsString();
    }
}
