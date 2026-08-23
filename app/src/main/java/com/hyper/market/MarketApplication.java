package com.hyper.market;

import android.app.Application;
import android.os.Build;

import java.util.concurrent.atomic.AtomicBoolean;

import org.lsposed.hiddenapibypass.HiddenApiBypass;
import com.hyper.market.installer.DownloadNotification;
import com.hyper.market.installer.DownloadTaskStore;

public final class MarketApplication extends Application {
    private final AtomicBoolean updateAutoRefreshClaimed = new AtomicBoolean();

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 28) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
        if (!DownloadTaskStore.hasTask(this)) {
            DownloadNotification.cancelOngoing(this);
        }
    }

    public boolean claimUpdateAutoRefresh() {
        return updateAutoRefreshClaimed.compareAndSet(false, true);
    }

    public void releaseUpdateAutoRefresh() {
        updateAutoRefreshClaimed.set(false);
    }
}
