package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Reads and writes Android settings, abstracted so the tools can be unit-tested
 * with a fake and the real provider plumbing lives in one place.
 *
 * {@code namespace} is one of {@code secure}, {@code global}, {@code system}.
 */
public interface SettingsAccess {

    @Nullable
    String get(@NonNull String namespace, @NonNull String key, int userId) throws Exception;

    void put(@NonNull String namespace, @NonNull String key, @Nullable String value, int userId) throws Exception;
}
