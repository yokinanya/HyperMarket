package com.hyper.market.api;

import android.content.Context;
import android.os.Build;
import android.util.Log;


/** Reads the optional Xiaomi identity values used by the original market client. */
final class XiaomiDeviceIdentity {
    private static final String TAG = "XiaomiIdentity";
    private static final String SETTINGS_FILE = "market_settings";
    private static final String SYSTEM_DCTX_KEY = "xiaomi_system_dctx";
    private static final String SERVER_DCTX_KEY = "xiaomi_server_dctx";
    private static final String LEGACY_DCTX_KEY = "xiaomi_dctx";
    private static final String EXPERIMENT_ID_KEY = "xiaomi_exp_id";
    private static final String TZ_SIGN_KEY = "xiaomi_tz_sign";
    private static final String TZ_CREATE_TIME_KEY = "xiaomi_tz_sign_create_time";
    private static final long DAY_MILLIS = 86_400_000L;
    private static final long TZ_CACHE_MILLIS = 7_200_000L;

    private final Context context;
    private final XiaomiIdentityServices services;
    private volatile Identity cached;

    XiaomiDeviceIdentity(Context context) {
        this.context = context;
        this.services = new XiaomiIdentityServices(context);
    }

    Identity read() {
        Identity value = cached;
        if (value != null) return value;
        synchronized (this) {
            value = cached;
            if (value == null) {
                value = load();
                cached = value;
            }
            return value;
        }
    }

    String readExperimentIds(String fallback) {
        String stored = preferences().getString(EXPERIMENT_ID_KEY, "");
        return isBlank(stored) ? fallback : stored;
    }

    void storeServerIdentity(String dctx, String experimentIds) {
        android.content.SharedPreferences.Editor editor = preferences().edit();
        if (!isBlank(dctx)) editor.putString(SERVER_DCTX_KEY, dctx);
        if (!isBlank(experimentIds)) editor.putString(EXPERIMENT_ID_KEY, experimentIds);
        editor.apply();
        synchronized (this) {
            cached = null;
        }
    }

    private Identity load() {
        long activeMillis = activeMillis();
        String installDay = String.valueOf(Math.max(activeMillis / DAY_MILLIS, 1L));
        String dctx = readDctx();
        Token token = readTrustZone();
        return new Identity(
                String.valueOf(activeMillis),
                installDay,
                dctx,
                token == null ? "" : token.nonce,
                token == null ? "" : token.sign,
                xmsfVersion());
    }

    private long activeMillis() {
        long installTime = firstInstallTime(context.getPackageName(), System.currentTimeMillis());
        long originalInstallTime = firstInstallTime("com.hyper.market", Long.MAX_VALUE);
        if (originalInstallTime < installTime) {
            installTime = originalInstallTime;
        }
        return Math.max(System.currentTimeMillis() - installTime, 1L);
    }

    private long firstInstallTime(String packageName, long fallback) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).firstInstallTime;
        } catch (Exception exception) {
            if (packageName.equals(context.getPackageName())) {
                Log.w(TAG, "无法读取首次安装时间，使用当前时间", exception);
            }
            return fallback;
        }
    }

    private String xmsfVersion() {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo("com.xiaomi.xmsf", 0);
            return Build.VERSION.SDK_INT >= 28
                    ? String.valueOf(info.getLongVersionCode())
                    : String.valueOf(info.versionCode);
        } catch (Exception exception) {
            Log.w(TAG, "无法读取小米框架版本", exception);
            return "";
        }
    }

    private String readDctx() {
        android.content.SharedPreferences preferences = preferences();
        String system = preferences.getString(SYSTEM_DCTX_KEY, "");
        if (isBlank(system)) {
            system = services.readDctx();
            if (!isBlank(system)) {
                preferences.edit().putString(SYSTEM_DCTX_KEY, system).apply();
            }
        }
        if (!isBlank(system)) return system;
        String server = preferences.getString(SERVER_DCTX_KEY, "");
        if (!isBlank(server)) return server;
        return preferences.getString(LEGACY_DCTX_KEY, "");
    }

    private Token readTrustZone() {
        android.content.SharedPreferences preferences = preferences();
        long createdAt = preferences.getLong(TZ_CREATE_TIME_KEY, 0L);
        String cachedSign = preferences.getString(TZ_SIGN_KEY, "");
        long age = System.currentTimeMillis() - createdAt;
        if (!isBlank(cachedSign)
                && age >= 0 && age < TZ_CACHE_MILLIS) {
            return new Token(String.valueOf(createdAt), cachedSign);
        }
        String nonce = String.valueOf(System.currentTimeMillis());
        String fid = services.readSecurityDeviceId();
        String sign = isBlank(fid) ? "" : services.signTrustZone(fid + "," + nonce);
        if (isBlank(sign)) return null;
        preferences.edit()
                .putString(TZ_SIGN_KEY, sign)
                .putLong(TZ_CREATE_TIME_KEY, Long.parseLong(nonce))
                .apply();
        return new Token(nonce, sign);
    }

    private android.content.SharedPreferences preferences() {
        return context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class Identity {
        final String activeTimeInterval;
        final String installDay;
        final String dctx;
        final String tzNonce;
        final String tzSign;
        final String xmsfVersion;

        private Identity(String activeTimeInterval, String installDay, String dctx,
                         String tzNonce, String tzSign, String xmsfVersion) {
            this.activeTimeInterval = activeTimeInterval;
            this.installDay = installDay;
            this.dctx = dctx;
            this.tzNonce = tzNonce;
            this.tzSign = tzSign;
            this.xmsfVersion = xmsfVersion;
        }
    }

    private static final class Token {
        private final String nonce;
        private final String sign;

        private Token(String nonce, String sign) {
            this.nonce = nonce;
            this.sign = sign;
        }
    }
}
