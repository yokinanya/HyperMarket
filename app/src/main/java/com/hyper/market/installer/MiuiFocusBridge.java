package com.hyper.market.installer;

import android.app.Notification;
import android.content.Context;
import android.content.ContentProviderClient;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.hyper.market.R;

import java.util.function.Consumer;

public final class MiuiFocusBridge {
    private static final String TAG = "MiuiFocusBridge";
    private static final String FOCUS_PROVIDER = "miui.statusbar.notification.public";
    private static final String PICS = "miui.focus.pics";

    private MiuiFocusBridge() { }

    public static boolean apply(Context context, Notification notification,
                                String title, String content, boolean enabled,
                                boolean firstFloat) {
        if (!enabled) return false;
        Snapshot snapshot = Snapshot.read(context);
        if (!snapshot.supported()) {
            Log.w(TAG, "超级岛通知不可用：" + snapshot);
            return false;
        }
        try {
            Bundle extras = notification.extras;
            extras.putAll(buildFocusExtras(snapshot.protocol, title, content, firstFloat));
            Bundle pictures = new Bundle();
            pictures.putParcelable("key_logo", Icon.createWithResource(context, R.drawable.ic_launcher));
            extras.putBundle(PICS, pictures);
            return true;
        } catch (Exception exception) {
            Log.e(TAG, "写入超级岛通知参数失败", exception);
            return false;
        }
    }

    private static Bundle buildFocusExtras(
            int protocol, String title, String content, boolean firstFloat) {
        try {
            Class<?> notificationClass = Class.forName(
                    "com.xzakota.hyper.notification.focus.FocusNotification");
            String methodName = protocol >= 3 ? "buildV3" : "buildV2";
            Consumer<Object> consumer = template -> {
                try {
                    configureTemplate(template, title, content, protocol >= 3, firstFloat);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("HyperNotification 模板配置失败", exception);
                }
            };
            Object value = notificationClass.getMethod(methodName, Consumer.class)
                    .invoke(null, consumer);
            return (Bundle) value;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("HyperNotification API 不可用", exception);
        }
    }

    private static void configureTemplate(
            Object template,
            String title,
            String content,
            boolean v3,
            boolean firstFloat) throws ReflectiveOperationException {
        invoke(template, "setShowSmallIcon", Boolean.class, true);
        invoke(template, "setUpdatable", Boolean.class, true);
        invoke(template, "setEnableFloat", Boolean.class, false);
        if (!v3) invoke(template, "setTicker", String.class, title);
        configureTextBlock(template, "baseInfo", title, content);
        configureTextBlock(template, "hintInfo", title, content);
        if (v3) {
            invoke(template, "setShowNotification", Boolean.class, false);
            invoke(template, "setIslandFirstFloat", Boolean.class, firstFloat);
            configureIsland(template, title, content);
            invoke(template, "chatInfo", Consumer.class, (Consumer<Object>) info -> {
                try {
                    invoke(info, "setTitle", String.class, title);
                    invoke(info, "setContent", String.class, content);
                    invoke(info, "setPicProfile", String.class, "key_logo");
                    invoke(info, "setPicProfileDark", String.class, "key_logo");
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("HyperNotification 聊天模板配置失败", exception);
                }
            });
        }
    }

    private static void configureIsland(Object template, String title, String content)
            throws ReflectiveOperationException {
        invoke(template, "island", Consumer.class, (Consumer<Object>) island -> {
            try {
                invoke(island, "setIslandProperty", Integer.class, 1);
                invoke(island, "setIslandPriority", Integer.class, 1);
                configureSmallIsland(island, title);
                configureBigIsland(island, title, content);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("HyperNotification 岛模板配置失败", exception);
            }
        });
    }

    private static void configureSmallIsland(Object island, String title)
            throws ReflectiveOperationException {
        invoke(island, "smallIslandArea", Consumer.class, (Consumer<Object>) area -> {
            try {
                invoke(area, "picInfo", Consumer.class,
                        (Consumer<Object>) pic -> configureIslandPic(pic, title));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("HyperNotification 小岛配置失败", exception);
            }
        });
    }

    private static void configureBigIsland(Object island, String title, String content)
            throws ReflectiveOperationException {
        invoke(island, "bigIslandArea", Consumer.class, (Consumer<Object>) area -> {
            try {
                invoke(area, "imageTextInfoLeft", Consumer.class, (Consumer<Object>) info -> {
                    try {
                        invoke(info, "setType", Integer.class, 1);
                        invoke(info, "picInfo", Consumer.class,
                                (Consumer<Object>) pic -> configureIslandPic(pic, title));
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("HyperNotification 大岛图标配置失败", exception);
                    }
                });
                configureBigIslandText(area, title, content);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("HyperNotification 大岛配置失败", exception);
            }
        });
    }

    private static void configureBigIslandText(Object area, String title, String content)
            throws ReflectiveOperationException {
        invoke(area, "imageTextInfoRight", Consumer.class, (Consumer<Object>) info -> {
            try {
                invoke(info, "setType", Integer.class, 2);
                invoke(info, "textInfo", Consumer.class, (Consumer<Object>) text -> {
                    try {
                        invoke(text, "setTitle", String.class, title);
                        invoke(text, "setContent", String.class, content);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("HyperNotification 大岛文字配置失败", exception);
                    }
                });
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("HyperNotification 大岛文字区域配置失败", exception);
            }
        });
    }

    private static void configureIslandPic(Object pic, String title) {
        try {
            invoke(pic, "setType", Integer.class, 1);
            invoke(pic, "setPic", String.class, "key_logo");
            invoke(pic, "setContentDescription", String.class, title);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("HyperNotification 岛图标配置失败", exception);
        }
    }

    private static void configureTextBlock(
            Object template,
            String methodName,
            String title,
            String content) throws ReflectiveOperationException {
        invoke(template, methodName, Consumer.class, (Consumer<Object>) info -> {
            try {
                invoke(info, "setType", Integer.class, 1);
                invoke(info, "setTitle", String.class, title);
                invoke(info, "setContent", String.class, content);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("HyperNotification 文本模板配置失败", exception);
            }
        });
    }

    private static Object invoke(
            Object target,
            String methodName,
            Class<?> parameterType,
            Object argument) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName, parameterType).invoke(target, argument);
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
                Bundle result = callFocusProvider(context, arguments);
                return result != null && result.getBoolean("canShowFocus", false);
            } catch (Exception exception) {
                return false;
            }
        }

        private static Bundle callFocusProvider(Context context, Bundle arguments)
                throws Exception {
            if (Build.VERSION.SDK_INT >= 29) {
                return context.getContentResolver().call(
                        FOCUS_PROVIDER, "canShowFocus", null, arguments);
            }
            ContentProviderClient client = context.getContentResolver()
                    .acquireUnstableContentProviderClient(FOCUS_PROVIDER);
            if (client == null) {
                return null;
            }
            try {
                return client.call("canShowFocus", null, arguments);
            } finally {
                client.close();
            }
        }

        private boolean supported() {
            return Build.VERSION.SDK_INT >= 27 &&
                    (protocol == 2 || (protocol >= 3 && island)) && permission;
        }

        @Override
        public String toString() {
            return "protocol=" + protocol + ", island=" + island + ", permission=" + permission;
        }
    }
}
