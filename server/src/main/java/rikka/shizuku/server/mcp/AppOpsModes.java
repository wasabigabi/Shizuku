package rikka.shizuku.server.mcp;

/**
 * Maps between AppOps mode names and codes.
 *
 * The codes are the stable platform constants (AppOpsManager.MODE_*); they are
 * redeclared here so this helper stays free of hidden-API references and can be
 * unit-tested on a plain JVM.
 */
final class AppOpsModes {

    static final int MODE_ALLOWED = 0;
    static final int MODE_IGNORED = 1;
    static final int MODE_ERRORED = 2;
    static final int MODE_DEFAULT = 3;
    static final int MODE_FOREGROUND = 4;

    private AppOpsModes() {
    }

    /** Parses a mode from a human name ("allow", "deny", ...) or a number. */
    static int fromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "allow":
            case "allowed":
                return MODE_ALLOWED;
            case "ignore":
            case "ignored":
            case "deny":
            case "denied":
                return MODE_IGNORED;
            case "error":
            case "errored":
                return MODE_ERRORED;
            case "default":
                return MODE_DEFAULT;
            case "foreground":
            case "fg":
                return MODE_FOREGROUND;
            default:
                try {
                    int v = Integer.parseInt(s.trim());
                    if (v < MODE_ALLOWED || v > MODE_FOREGROUND) {
                        throw new IllegalArgumentException("mode out of range: " + v);
                    }
                    return v;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("unknown mode: " + s);
                }
        }
    }

    static String name(int mode) {
        switch (mode) {
            case MODE_ALLOWED: return "allow";
            case MODE_IGNORED: return "ignore";
            case MODE_ERRORED: return "error";
            case MODE_DEFAULT: return "default";
            case MODE_FOREGROUND: return "foreground";
            default: return "mode_" + mode;
        }
    }
}
