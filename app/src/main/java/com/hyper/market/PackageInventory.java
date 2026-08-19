package com.hyper.market;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.hyper.market.model.InstalledPackageInfo;

import java.util.ArrayList;
import java.util.List;

public final class PackageInventory {
    private static final String GET_INSTALLED_APPS_PERMISSION =
            "com.android.permission.GET_INSTALLED_APPS";
    private static final String XIAOMI_MARKET_PACKAGE = "com.xiaomi.market";
    private static final String DEFAULT_SPLITS = "0";
    private static final String DEFAULT_HASH = "0";
    private static final String DEFAULT_APK_SOURCE = "0";

    public List<InstalledPackageInfo> scan(Context context) {
        requirePackageVisibility(context);
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);
        List<InstalledPackageInfo> result = new ArrayList<>(packages.size());
        for (PackageInfo packageInfo : packages) {
            if (context.getPackageName().equals(packageInfo.packageName)) {
                continue;
            }
            result.add(toInstalledPackage(packageManager, packageInfo));
        }
        return result;
    }

    private void requirePackageVisibility(Context context) {
        if (!isPermissionDeclared(context) || context.checkSelfPermission(GET_INSTALLED_APPS_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            throw new SecurityException("缺少已安装应用访问权限，请先允许 GET_INSTALLED_APPS");
        }
    }

    private boolean isPermissionDeclared(Context context) {
        try {
            context.getPackageManager().getPermissionInfo(GET_INSTALLED_APPS_PERMISSION, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private InstalledPackageInfo toInstalledPackage(
            PackageManager packageManager,
            PackageInfo packageInfo) {
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        String installer = installerPackage(packageManager, packageInfo.packageName);
        String installedByMarket = XIAOMI_MARKET_PACKAGE.equals(installer) ? "1" : "0";
        return new InstalledPackageInfo(
                packageInfo.packageName,
                packageInfo.versionName == null ? "" : packageInfo.versionName,
                versionCode(packageInfo),
                isSystemApp(applicationInfo),
                installedByMarket,
                splitNames(packageInfo),
                DEFAULT_HASH,
                apkSource(applicationInfo));
    }

    private String installerPackage(PackageManager packageManager, String packageName) {
        try {
            String installer = packageManager.getInstallerPackageName(packageName);
            return installer == null ? "" : installer;
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private long versionCode(PackageInfo packageInfo) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }

    private boolean isSystemApp(ApplicationInfo applicationInfo) {
        if (applicationInfo == null) {
            return false;
        }
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private String splitNames(PackageInfo packageInfo) {
        if (packageInfo.splitNames == null || packageInfo.splitNames.length == 0) {
            return DEFAULT_SPLITS;
        }
        StringBuilder result = new StringBuilder("1:[");
        for (int index = 0; index < packageInfo.splitNames.length; index++) {
            if (index > 0) {
                result.append('#');
            }
            result.append(packageInfo.splitNames[index]);
        }
        return result.append(']').toString();
    }

    private String apkSource(ApplicationInfo applicationInfo) {
        if (applicationInfo == null || applicationInfo.sourceDir == null
                || applicationInfo.sourceDir.isEmpty()) {
            return DEFAULT_APK_SOURCE;
        }
        return applicationInfo.sourceDir;
    }
}
