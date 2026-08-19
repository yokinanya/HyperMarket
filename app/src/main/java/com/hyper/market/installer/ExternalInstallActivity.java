package com.hyper.market.installer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.hyper.market.UpdateStore;
import com.hyper.market.model.MarketAppInfo;

public final class ExternalInstallActivity extends Activity {
    private static final int REQUEST_INSTALL = 42;
    private static final String EXTRA_INTENTS = "external_install_intents";
    private static final String EXTRA_PACKAGE = "external_package_name";
    private static final String EXTRA_DISPLAY = "external_display_name";
    private static final String EXTRA_VERSION = "external_version_name";
    private static final String EXTRA_CODE = "external_version_code";
    private static final String EXTRA_FIRST = "external_first_install";

    public static void launch(android.content.Context context, java.util.List<Intent> intents,
                              InstallOptions options) {
        java.util.ArrayList<Intent> parcelableIntents = new java.util.ArrayList<>(intents);
        Intent relay = new Intent(context, ExternalInstallActivity.class)
                .putParcelableArrayListExtra(EXTRA_INTENTS, parcelableIntents)
                .putExtra(EXTRA_PACKAGE, options.getPackageName())
                .putExtra(EXTRA_DISPLAY, options.getDisplayName())
                .putExtra(EXTRA_VERSION, options.getVersionName())
                .putExtra(EXTRA_CODE, options.getVersionCode())
                .putExtra(EXTRA_FIRST, options.isFirstInstall())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(relay);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        java.util.ArrayList<Intent> intents = getIntent().getParcelableArrayListExtra(EXTRA_INTENTS);
        if (intents == null || intents.isEmpty()) {
            throw new IllegalStateException("第三方安装器调用缺少安装 Intent");
        }
        startIntent(intents, 0);
    }

    private void startIntent(java.util.ArrayList<Intent> intents, int index) {
        try {
            startActivityForResult(intents.get(index), REQUEST_INSTALL);
        } catch (android.content.ActivityNotFoundException exception) {
            if (index + 1 < intents.size()) {
                startIntent(intents, index + 1);
                return;
            }
            throw exception;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL) return;
        if (resultCode == RESULT_OK) {
            recordSuccess();
            Toast.makeText(this, "第三方安装器已返回安装成功", Toast.LENGTH_LONG).show();
        } else {
            DownloadNotification.failure(this, "第三方安装器取消或未返回成功状态");
            Toast.makeText(this, "第三方安装器未确认安装成功", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void recordSuccess() {
        MarketAppInfo app = new MarketAppInfo.Builder()
                .packageName(getIntent().getStringExtra(EXTRA_PACKAGE))
                .displayName(getIntent().getStringExtra(EXTRA_DISPLAY))
                .versionName(getIntent().getStringExtra(EXTRA_VERSION))
                .versionCode(getIntent().getLongExtra(EXTRA_CODE, 0))
                .build();
        new UpdateStore(this).recordHistory(
                app, getIntent().getBooleanExtra(EXTRA_FIRST, false));
        DownloadNotification.complete(this, app.getDisplayName());
    }
}
