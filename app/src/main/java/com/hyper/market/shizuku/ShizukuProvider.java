package com.hyper.market.shizuku;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.hyper.market.installer.ShizukuBridge;

import moe.shizuku.api.BinderContainer;

public final class ShizukuProvider extends ContentProvider {
    @Override
    public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (extras == null) {
            return Bundle.EMPTY;
        }
        extras.setClassLoader(BinderContainer.class.getClassLoader());
        if ("sendBinder".equals(method)) {
            BinderContainer container = extras.getParcelable("moe.shizuku.privileged.api.intent.extra.BINDER");
            if (container == null || container.binder == null) {
                throw new IllegalStateException("Shizuku 未返回服务 Binder");
            }
            ShizukuBridge.setServiceBinder(container.binder);
        }
        return Bundle.EMPTY;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
