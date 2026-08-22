package com.hyper.market.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Binder adapters matching the optional services used by the original app. */
final class XiaomiIdentityServices {
    private static final String TAG = "XiaomiIdentity";
    private static final String CREDENTIAL_ACTION =
            "com.xiaomi.finddevice.action.BIND_SECURITY_DEVICE_CREDENTIAL";
    private static final String CREDENTIAL_PACKAGE = "com.xiaomi.finddevice";
    private static final String ACCOUNT_CREDENTIAL_ACTION =
            "com.xiaomi.account.action.BIND_SECURITY_DEVICE_CREDENTIAL";
    private static final String ACCOUNT_PACKAGE = "com.xiaomi.account";
    private static final String CREDENTIAL_DESCRIPTOR =
            "com.xiaomi.security.devicecredential.ISecurityDeviceCredentialManager";
    private static final long SERVICE_TIMEOUT_SECONDS = 25L;

    private final Context context;

    XiaomiIdentityServices(Context context) {
        this.context = context;
    }

    String readDctx() {
        String accountDctx = readDctx(ACCOUNT_CREDENTIAL_ACTION, ACCOUNT_PACKAGE);
        return isBlank(accountDctx)
                ? readDctx(CREDENTIAL_ACTION, CREDENTIAL_PACKAGE) : accountDctx;
    }

    String readSecurityDeviceId() {
        return readDctx();
    }

    String signTrustZone(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] signed = credentialSign(bytes, ACCOUNT_CREDENTIAL_ACTION, ACCOUNT_PACKAGE);
        if (signed == null) signed = credentialSign(bytes, CREDENTIAL_ACTION, CREDENTIAL_PACKAGE);
        return hex(signed);
    }

    private String readDctx(String action, String packageName) {
        try (BoundService service = bind(action, packageName)) {
            if (service == null || !credentialSupported(service.binder)) return "";
            return credentialString(service.binder);
        } catch (Exception exception) {
            Log.w(TAG, "无法从设备凭据服务读取 dctx: " + packageName, exception);
            return "";
        }
    }

    private boolean credentialSupported(IBinder binder) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel response = Parcel.obtain();
        try {
            request.writeInterfaceToken(CREDENTIAL_DESCRIPTOR);
            if (!binder.transact(1, request, response, 0)) return false;
            response.readException();
            return response.readInt() != 0;
        } finally {
            response.recycle();
            request.recycle();
        }
    }

    private String credentialString(IBinder binder) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel response = Parcel.obtain();
        try {
            request.writeInterfaceToken(CREDENTIAL_DESCRIPTOR);
            if (!binder.transact(2, request, response, 0)) return "";
            response.readException();
            String value = response.readString();
            return value == null ? "" : value;
        } finally {
            response.recycle();
            request.recycle();
        }
    }

    private byte[] credentialSign(byte[] payload, String action, String packageName) {
        try (BoundService service = bind(action, packageName)) {
            if (service == null || !credentialSupported(service.binder)) return null;
            Parcel request = Parcel.obtain();
            Parcel response = Parcel.obtain();
            try {
                request.writeInterfaceToken(CREDENTIAL_DESCRIPTOR);
                request.writeInt(1);
                request.writeByteArray(payload);
                request.writeInt(1);
                if (!service.binder.transact(3, request, response, 0)) return null;
                response.readException();
                return response.createByteArray();
            } finally {
                response.recycle();
                request.recycle();
            }
        } catch (Exception exception) {
            Log.w(TAG, "TrustZone 签名失败: " + packageName, exception);
            return null;
        }
    }

    private BoundService bind(String action, String packageName) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        BinderHolder holder = new BinderHolder(latch);
        boolean bound;
        try {
            bound = context.bindService(new Intent(action).setPackage(packageName), holder,
                    Context.BIND_AUTO_CREATE);
        } catch (SecurityException exception) {
            Log.w(TAG, "设备服务权限不足: " + action, exception);
            return null;
        }
        if (!bound || !latch.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                || holder.binder == null) {
            if (bound) holder.unbind(context);
            return null;
        }
        return new BoundService(context, holder, holder.binder);
    }

    private String hex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value));
        }
        return result.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class BinderHolder implements ServiceConnection {
        private final CountDownLatch latch;
        private IBinder binder;

        private BinderHolder(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = service;
            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            latch.countDown();
        }

        private void unbind(Context context) {
            try {
                context.unbindService(this);
            } catch (IllegalArgumentException ignored) {
                // Optional service may already have disconnected.
            }
        }
    }

    private static final class BoundService implements AutoCloseable {
        private final Context context;
        private final BinderHolder holder;
        private final IBinder binder;

        private BoundService(Context context, BinderHolder holder, IBinder binder) {
            this.context = context;
            this.holder = holder;
            this.binder = binder;
        }

        @Override
        public void close() {
            holder.unbind(context);
        }
    }

}
