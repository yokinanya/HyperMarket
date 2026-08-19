package moe.shizuku.api;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public final class BinderContainer implements Parcelable {
    public static final Creator<BinderContainer> CREATOR = new Creator<>() {
        @Override
        public BinderContainer createFromParcel(Parcel source) {
            return new BinderContainer(source.readStrongBinder());
        }

        @Override
        public BinderContainer[] newArray(int size) {
            return new BinderContainer[size];
        }
    };

    public final IBinder binder;

    public BinderContainer(IBinder binder) {
        this.binder = binder;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeStrongBinder(binder); }
}
