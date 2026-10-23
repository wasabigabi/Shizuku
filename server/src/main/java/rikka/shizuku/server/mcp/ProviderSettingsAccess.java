package rikka.shizuku.server.mcp;

import android.content.IContentProvider;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import rikka.hidden.compat.ActivityManagerApis;
import rikka.shizuku.server.api.IContentProviderUtils;
import rikka.shizuku.server.util.Logger;

/**
 * {@link SettingsAccess} backed by the system "settings" content provider,
 * reached the same way the server already reaches providers to deliver its
 * binder (see {@code ShizukuService#sendBinderToUserApp}).
 *
 * The provider enforces write permission against the server's own uid
 * (shell/root), so this grants no more than the Shizuku process already has.
 */
public final class ProviderSettingsAccess implements SettingsAccess {

    private static final Logger LOGGER = new Logger("ShizukuMcpSettings");

    private static final String AUTHORITY = "settings";
    private static final String CALLING_PKG = "com.android.shell";
    // Stable Settings provider call constants.
    private static final String KEY_USER = "_user";
    private static final String KEY_VALUE = "value";

    @Nullable
    @Override
    public String get(@NonNull String namespace, @NonNull String key, int userId) throws Exception {
        IContentProvider provider = acquire(userId);
        try {
            Bundle extras = new Bundle();
            extras.putInt(KEY_USER, userId);
            Bundle res = IContentProviderUtils.callCompat(
                    provider, CALLING_PKG, AUTHORITY, "GET_" + namespace, key, extras);
            return res != null ? res.getString(KEY_VALUE) : null;
        } finally {
            release();
        }
    }

    @Override
    public void put(@NonNull String namespace, @NonNull String key, @Nullable String value, int userId) throws Exception {
        IContentProvider provider = acquire(userId);
        try {
            Bundle extras = new Bundle();
            extras.putString(KEY_VALUE, value);
            extras.putInt(KEY_USER, userId);
            IContentProviderUtils.callCompat(
                    provider, CALLING_PKG, AUTHORITY, "PUT_" + namespace, key, extras);
        } finally {
            release();
        }
    }

    private IContentProvider acquire(int userId) throws Exception {
        // token=null: like ShizukuService#sendBinderToUserApp, a null token lets
        // the provider be reference-counted rather than keyed by a BinderProxy.
        IContentProvider provider = ActivityManagerApis.getContentProviderExternal(
                AUTHORITY, userId, null, AUTHORITY);
        if (provider == null) {
            throw new IllegalStateException("settings provider unavailable for user " + userId);
        }
        return provider;
    }

    private void release() {
        try {
            ActivityManagerApis.removeContentProviderExternal(AUTHORITY, null);
        } catch (Throwable tr) {
            LOGGER.w(tr, "removeContentProviderExternal(settings)");
        }
    }
}
