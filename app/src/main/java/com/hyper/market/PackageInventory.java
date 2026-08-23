package com.hyper.market;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.hyper.market.model.InstalledPackageInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public final class PackageInventory {
    private static final String GET_INSTALLED_APPS_PERMISSION =
            "com.android.permission.GET_INSTALLED_APPS";
    private static final String XIAOMI_MARKET_PACKAGE = "com.xiaomi.market";
    private static final String ORIGINAL_APP_PACKAGE = "com.hyper.market";
    private static final String DEFAULT_SPLITS = "0";
    private static final String DEFAULT_HASH = "0";
    private static final String DEFAULT_APK_SOURCE = "0";
    private static final String DEFAULT_INSTALLED_BY_MARKET = "0";
    private static final String CORE_PACKAGE = "com.miui.core";
    private static final int ORIGINAL_PACKAGE_FLAGS = 45_568;
    private static final long MATCH_APEX_FLAG = 1_073_741_824L;

    public List<InstalledPackageInfo> scan(Context context) {
        requirePackageVisibility(context);
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages = installedPackages(packageManager);
        List<InstalledPackageInfo> result = new ArrayList<>(packages.size());
        for (PackageInfo packageInfo : packages) {
            if (context.getPackageName().equals(packageInfo.packageName)
                    || ORIGINAL_APP_PACKAGE.equals(packageInfo.packageName)) {
                continue;
            }
            result.add(toInstalledPackage(packageManager, packageInfo));
        }
        return orderAndAugment(result);
    }

    private List<InstalledPackageInfo> orderAndAugment(List<InstalledPackageInfo> packages) {
        LinkedHashMap<String, InstalledPackageInfo> unique = new LinkedHashMap<>();
        packages.stream()
                .sorted(Comparator.comparing(InstalledPackageInfo::getPackageName))
                .forEach(item -> unique.put(item.getPackageName(), item));
        unique.putIfAbsent(CORE_PACKAGE, new InstalledPackageInfo(
                CORE_PACKAGE, "", 0, true, DEFAULT_INSTALLED_BY_MARKET,
                DEFAULT_SPLITS, DEFAULT_HASH, DEFAULT_APK_SOURCE));
        return new ArrayList<>(unique.values());
    }

    @SuppressLint("WrongConstant")
    private List<PackageInfo> installedPackages(PackageManager packageManager) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(ORIGINAL_PACKAGE_FLAGS | MATCH_APEX_FLAG));
        }
        int flags = ORIGINAL_PACKAGE_FLAGS;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            flags |= (int) MATCH_APEX_FLAG;
        }
        return packageManager.getInstalledPackages(flags);
    }

    private void requirePackageVisibility(Context context) {
        if (!isPermissionDeclared(context) || context.checkSelfPermission(GET_INSTALLED_APPS_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        throw new SecurityException("缺少已安装应用访问权限，请先允许 GET_INSTALLED_APPS");
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
            PackageManager packageManager, PackageInfo packageInfo) {
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
        int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        if ((applicationInfo.flags & systemFlags) != 0) {
            return true;
        }
        String sourceDir = applicationInfo.sourceDir;
        if (sourceDir == null) {
            return false;
        }
        String[] systemRoots = {
                "/system/", "/system_ext/", "/product/", "/vendor/", "/odm/", "/oem/"
        };
        for (String root : systemRoots) {
            if (sourceDir.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private String splitNames(PackageInfo packageInfo) {
        if (packageInfo.splitNames == null || packageInfo.splitNames.length == 0) {
            return DEFAULT_SPLITS;
        }
        StringBuilder result = new StringBuilder("1:[");
        int appended = 0;
        for (int index = 0; index < packageInfo.splitNames.length; index++) {
            String split = packageInfo.splitNames[index];
            if (split == null || split.isEmpty()) {
                continue;
            }
            if (appended > 0) {
                result.append('#');
            }
            result.append(split);
            appended++;
        }
        return appended == 0 ? DEFAULT_SPLITS : result.append(']').toString();
    }

    private String apkSource(ApplicationInfo applicationInfo) {
        if (applicationInfo == null || applicationInfo.sourceDir == null
                || applicationInfo.sourceDir.isEmpty()) {
            return DEFAULT_APK_SOURCE;
        }
        return applicationInfo.sourceDir;
    }
}
