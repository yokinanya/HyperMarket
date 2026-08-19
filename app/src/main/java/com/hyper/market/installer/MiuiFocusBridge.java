package com.hyper.market.installer;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import com.hyper.market.R;

import org.json.JSONObject;

public final class MiuiFocusBridge {
    private static final String TAG = "MiuiFocusBridge";
    private static final String FOCUS_PROVIDER = "miui.statusbar.notification.public";
    private static final String PARAM = "miui.focus.param";
    private static final String PICS = "miui.focus.pics";

    private MiuiFocusBridge() { }

    public static boolean apply(Context context, Notification notification,
                                String title, String content, boolean enabled) {
        if (!enabled) return false;
        Snapshot snapshot = Snapshot.read(context);
        if (!snapshot.supported()) {
            Log.w(TAG, "超级岛通知不可用：" + snapshot);
            return false;
        }
        try {
            Bundle extras = notification.extras;
            extras.putString(PARAM, focusParam(snapshot.protocol, title, content));
            Bundle pictures = new Bundle();
            pictures.putParcelable("key_logo", Icon.createWithResource(context, R.drawable.ic_launcher));
            extras.putBundle(PICS, pictures);
            return true;
        } catch (Exception exception) {
            Log.e(TAG, "写入超级岛通知参数失败", exception);
            return false;
        }
    }

    private static String focusParam(int protocol, String title, String content) throws Exception {
        JSONObject param = new JSONObject()
                .put("ticker", title)
                .put("showSmallIcon", true)
                .put("updatable", true)
                .put("enableFloat", true);
        if (protocol >= 3) {
            param.put("isShowNotification", true)
                    .put("islandFirstFloat", true)
                    .put("chatInfo", new JSONObject()
                            .put("title", title)
                            .put("content", content)
                            .put("picProfile", "key_logo")
                            .put("picProfileDark", "key_logo"));
        }
        return param.toString();
    }

    private static final class Snapshot {
        private final int protocol;
        private final boolean island;
        private final boolean permission;

        private Snapshot(int protocol, boolean island, boolean permission) {
            this.protocol = protocol;
            this.island = island;
            this.permission = permission;
        }

        private static Snapshot read(Context context) {
            int protocol = Settings.System.getInt(
                    context.getContentResolver(), "notification_focus_protocol", 0);
            boolean island = readIslandProperty();
            boolean permission = canShowFocus(context);
            return new Snapshot(protocol, island, permission);
        }

        private static boolean readIslandProperty() {
            try {
                Object value = Class.forName("android.os.SystemProperties")
                        .getDeclaredMethod("getBoolean", String.class, Boolean.TYPE)
                        .invoke(null, "persist.sys.feature.island", false);
                return Boolean.TRUE.equals(value);
            } catch (Exception exception) {
                return false;
            }
        }

        private static boolean canShowFocus(Context context) {
            try {
                Bundle arguments = new Bundle();
                arguments.putString("package", context.getPackageName());
                Bundle result = context.getContentResolver().call(
                        FOCUS_PROVIDER, "canShowFocus", null, arguments);
                return result != null && result.getBoolean("canShowFocus", false);
            } catch (Exception exception) {
                return false;
            }
        }

        private boolean supported() {
            return (protocol == 2 || (protocol >= 3 && island)) && permission;
        }

        @Override
        public String toString() {
            return "protocol=" + protocol + ", island=" + island + ", permission=" + permission;
        }
    }
}
