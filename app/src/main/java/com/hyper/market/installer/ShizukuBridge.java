package com.hyper.market.installer;

import android.content.Context;
import android.content.pm.PackageInstaller;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ShizukuBridge {
    private static final String SERVICE_DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    private static final int ATTACH_TRANSACTION = 18;
    private static final int CHECK_PERMISSION_TRANSACTION = 16;
    private static final int SERVICE_TRANSACTION = 1;
    private static final int SHIZUKU_API_VERSION = 13;
    private static final int ACTIVITY_SERVICE_TRANSACTION = 1599296841;
    private static IBinder service;
    private static boolean attached;

    private ShizukuBridge() { }

    public static void setServiceBinder(IBinder binder) {
        service = binder;
        attached = false;
    }

    public static IBinder getServiceBinder() {
        return service;
    }

    static PackageInstaller packageInstaller(Context context) throws Exception {
        IBinder shizuku = serviceBinder(context.getPackageName());
        requirePermission(shizuku);
        IBinder packageManager = systemService("package");
        IInterface packageManagerInterface = asInterface(
                "android.content.pm.IPackageManager", new ShizukuBinderProxy(shizuku, packageManager));
        Object rawInstaller = noArgMethod(packageManagerInterface, "getPackageInstaller").invoke(
                packageManagerInterface);
        if (!(rawInstaller instanceof IInterface)) {
            throw new IllegalStateException("Shizuku 未返回 PackageInstaller 服务");
        }
        IBinder installerBinder = ((IInterface) rawInstaller).asBinder();
        IInterface installerInterface = asInterface(
                "android.content.pm.IPackageInstaller", new ShizukuBinderProxy(shizuku, installerBinder));
        return newPackageInstaller(
                installerInterface, InstallerIdentity.XIAOMI_MARKET_PACKAGE);
    }

    static PackageInstaller.Session openSession(PackageInstaller installer, int sessionId)
            throws Exception {
        PackageInstaller.Session direct = installer.openSession(sessionId);
        IInterface sessionInterface = sessionInterface(direct);
        Class<?> interfaceClass = Class.forName("android.content.pm.IPackageInstallerSession");
        IInterface proxy = asInterface(interfaceClass.getName(),
                new ShizukuBinderProxy(service, sessionInterface.asBinder()));
        Constructor<?> constructor = PackageInstaller.Session.class
                .getDeclaredConstructor(interfaceClass);
        constructor.setAccessible(true);
        return (PackageInstaller.Session) constructor.newInstance(proxy);
    }

    private static IInterface sessionInterface(PackageInstaller.Session session) throws Exception {
        for (Field field : PackageInstaller.Session.class.getDeclaredFields()) {
            if (!IInterface.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(session);
            if (value instanceof IInterface) return (IInterface) value;
        }
        throw new IllegalStateException("PackageInstaller Session 接口不可用");
    }

    private static IBinder serviceBinder(String packageName) throws Exception {
        if (service == null || !service.pingBinder()) {
            service = findServiceFromActivityManager();
        }
        if (service == null || !service.pingBinder()) {
            throw new IllegalStateException("Shizuku 服务未运行");
        }
        attach(service, packageName);
        return service;
    }

    private static void attach(IBinder binder, String packageName) throws Exception {
        if (attached) {
            return;
        }
        AppBinder application = new AppBinder();
        Bundle arguments = new Bundle();
        arguments.putInt("shizuku:attach-api-version", SHIZUKU_API_VERSION);
        arguments.putString("shizuku:attach-package-name", packageName);
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(SERVICE_DESCRIPTOR);
            request.writeStrongBinder(application);
            request.writeInt(1);
            arguments.writeToParcel(request, 0);
            binder.transact(ATTACH_TRANSACTION, request, reply, 0);
            reply.readException();
            attached = true;
        } finally {
            reply.recycle();
            request.recycle();
        }
    }

    private static void requirePermission(IBinder binder) throws Exception {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(SERVICE_DESCRIPTOR);
            binder.transact(CHECK_PERMISSION_TRANSACTION, request, reply, 0);
            reply.readException();
            if (reply.readInt() == 0) {
                throw new SecurityException("Shizuku 未授予本应用权限，请先在 Shizuku 中授权");
            }
        } finally {
            reply.recycle();
            request.recycle();
        }
    }

    private static IBinder findServiceFromActivityManager() throws Exception {
        IBinder activity = systemService("activity");
        if (activity == null) {
            return null;
        }
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken("android.app.IActivityManager");
            request.writeInt(2);
            activity.transact(ACTIVITY_SERVICE_TRANSACTION, request, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            reply.recycle();
            request.recycle();
        }
    }

    private static IBinder systemService(String name) throws Exception {
        Method method = Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String.class);
        return (IBinder) method.invoke(null, name);
    }

    private static IInterface asInterface(String className, IBinder binder) throws Exception {
        Class<?> stub = Class.forName(className + "$Stub");
        return (IInterface) stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private static Method noArgMethod(Object object, String name) {
        for (Method method : object.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        throw new IllegalStateException("Shizuku 服务缺少 " + name);
    }

    private static PackageInstaller newPackageInstaller(
            IInterface installer, String packageName) throws Exception {
        Class<?> interfaceClass = Class.forName("android.content.pm.IPackageInstaller");
        Constructor<?> constructor;
        Object value;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            constructor = PackageInstaller.class.getDeclaredConstructor(
                    interfaceClass, String.class, String.class, int.class);
            constructor.setAccessible(true);
            value = constructor.newInstance(installer, packageName, null, Process.myUid() / 100000);
        } else {
            constructor = PackageInstaller.class.getDeclaredConstructor(
                    interfaceClass, String.class, int.class);
            constructor.setAccessible(true);
            value = constructor.newInstance(installer, packageName, Process.myUid() / 100000);
        }
        return (PackageInstaller) value;
    }

    private static final class AppBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == 2) {
                data.enforceInterface("moe.shizuku.server.IShizukuApplication");
                Bundle response = data.readInt() == 0 ? null : Bundle.CREATOR.createFromParcel(data);
                if (response != null) {
                    response.getInt("shizuku:attach-reply-version", -1);
                }
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
}
