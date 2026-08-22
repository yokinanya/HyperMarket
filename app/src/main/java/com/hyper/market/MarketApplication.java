package com.hyper.market;

import android.app.Application;
import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

public final class MarketApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 28) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
    }
}
