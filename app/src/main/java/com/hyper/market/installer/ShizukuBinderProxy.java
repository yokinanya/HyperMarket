package com.hyper.market.installer;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

final class ShizukuBinderProxy implements IBinder {
    private static final String SERVICE_DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    private final IBinder service;
    private final IBinder target;

    ShizukuBinderProxy(IBinder service, IBinder target) {
        this.service = service;
        this.target = target;
    }

    @Override
    public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        Parcel request = Parcel.obtain();
        try {
            request.writeInterfaceToken(SERVICE_DESCRIPTOR);
            request.writeStrongBinder(target);
            request.writeInt(code);
            request.writeInt(flags);
            request.appendFrom(data, 0, data.dataSize());
            return service.transact(1, request, reply, 0);
        } finally {
            request.recycle();
        }
    }

    @Override public String getInterfaceDescriptor() throws RemoteException { return target.getInterfaceDescriptor(); }
    @Override public boolean pingBinder() { return target.pingBinder(); }
    @Override public boolean isBinderAlive() { return target.isBinderAlive(); }
    @Override public IInterface queryLocalInterface(String descriptor) { return null; }
    @Override public void dump(java.io.FileDescriptor fd, String[] args) throws RemoteException { target.dump(fd, args); }
    @Override public void dumpAsync(java.io.FileDescriptor fd, String[] args) throws RemoteException { target.dumpAsync(fd, args); }
    @Override public void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException { target.linkToDeath(recipient, flags); }
    @Override public boolean unlinkToDeath(DeathRecipient recipient, int flags) { return target.unlinkToDeath(recipient, flags); }
}
