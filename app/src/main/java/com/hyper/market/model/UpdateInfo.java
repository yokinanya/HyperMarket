package com.hyper.market.model;

public final class UpdateInfo {
    private final MarketAppInfo app;
    private final InstalledPackageInfo installedPackage;
    private final long diffSize;

    public UpdateInfo(MarketAppInfo app, InstalledPackageInfo installedPackage) {
        this(app, installedPackage, 0);
    }

    public UpdateInfo(MarketAppInfo app, InstalledPackageInfo installedPackage, long diffSize) {
        this.app = app;
        this.installedPackage = installedPackage;
        this.diffSize = diffSize;
    }

    public MarketAppInfo getApp() { return app; }
    public InstalledPackageInfo getInstalledPackage() { return installedPackage; }
    public long getDiffSize() { return diffSize; }
}
