package rikka.shizuku;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.hyper.market.installer.ShizukuBridge;

import moe.shizuku.api.BinderContainer;

public class ShizukuProvider extends ContentProvider {
    private static final String TAG = "ShizukuProvider";
    private static final String SEND_BINDER = "sendBinder";
    private static final String GET_BINDER = "getBinder";
    private static final String BINDER_KEY =
            "moe.shizuku.privileged.api.intent.extra.BINDER";

    @Override
    public void attachInfo(Context context, ProviderInfo providerInfo) {
        super.attachInfo(context, providerInfo);
        if (!providerInfo.exported || providerInfo.multiprocess) {
            throw new IllegalStateException(
                    "ShizukuProvider must be exported and single-process");
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (extras != null) {
            extras.setClassLoader(BinderContainer.class.getClassLoader());
        }
        if (SEND_BINDER.equals(method)) {
            receiveBinder(extras);
        }
        if (GET_BINDER.equals(method)) {
            return binderBundle(ShizukuBridge.getServiceBinder());
        }
        return Bundle.EMPTY;
    }

    private void receiveBinder(Bundle extras) {
        if (extras == null) {
            return;
        }
        BinderContainer container = extras.getParcelable(BINDER_KEY);
        if (container == null || container.binder == null) {
            Log.w(TAG, "Shizuku sent an empty binder");
            return;
        }
        ShizukuBridge.setServiceBinder(container.binder);
    }

    private Bundle binderBundle(IBinder binder) {
        if (binder == null || !binder.pingBinder()) {
            return null;
        }
        Bundle result = new Bundle();
        result.putParcelable(BINDER_KEY, new BinderContainer(binder));
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] args, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] args) { return 0; }
}
