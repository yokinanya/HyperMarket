package com.hyper.market.model;

public final class InstalledPackageInfo {
    private final String packageName;
    private final String versionName;
    private final long versionCode;
    private final boolean systemApp;
    private final String installedByMarket;
    private final String splits;
    private final String oldApkHash;
    private final String apkSource;

    public InstalledPackageInfo(
            String packageName,
            String versionName,
            long versionCode,
            boolean systemApp,
            String installedByMarket,
            String splits,
            String oldApkHash,
            String apkSource) {
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.systemApp = systemApp;
        this.installedByMarket = installedByMarket;
        this.splits = splits;
        this.oldApkHash = oldApkHash;
        this.apkSource = apkSource;
    }

    public String getPackageName() { return packageName; }
    public String getVersionName() { return versionName; }
    public long getVersionCode() { return versionCode; }
    public boolean isSystemApp() { return systemApp; }
    public String getInstalledByMarket() { return installedByMarket; }
    public String getSplits() { return splits; }
    public String getOldApkHash() { return oldApkHash; }
    public String getApkSource() { return apkSource; }
}
