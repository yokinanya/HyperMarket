package com.hyper.market.installer;

public final class InstallOptions {
    private final String installerMode;
    private final String packageName;
    private final String displayName;
    private final String versionName;
    private final long versionCode;
    private final boolean firstInstall;
    private final boolean noUserAction;
    private final boolean saveToDownloads;
    private final boolean deleteAfterInstall;
    private final String customInstallerPackage;
    private final String iconUrl;

    public InstallOptions(String installerMode, String packageName, String displayName,
                          String versionName, long versionCode, boolean firstInstall,
                          boolean noUserAction, boolean saveToDownloads,
                          boolean deleteAfterInstall) {
        this(installerMode, packageName, displayName, versionName, versionCode, firstInstall,
                noUserAction, saveToDownloads, deleteAfterInstall, "");
    }

    public InstallOptions(String installerMode, String packageName, String displayName,
                          String versionName, long versionCode, boolean firstInstall,
                          boolean noUserAction, boolean saveToDownloads,
                          boolean deleteAfterInstall, String customInstallerPackage) {
        this.installerMode = installerMode;
        this.packageName = packageName;
        this.displayName = displayName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.firstInstall = firstInstall;
        this.noUserAction = noUserAction;
        this.saveToDownloads = saveToDownloads;
        this.deleteAfterInstall = deleteAfterInstall;
        this.customInstallerPackage = customInstallerPackage == null ? "" : customInstallerPackage.trim();
        this.iconUrl = "";
    }

    public InstallOptions(String installerMode, String packageName, String displayName,
                          String versionName, long versionCode, boolean firstInstall,
                          boolean noUserAction, boolean saveToDownloads,
                          boolean deleteAfterInstall, String customInstallerPackage,
                          String iconUrl) {
        this.installerMode = installerMode;
        this.packageName = packageName;
        this.displayName = displayName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.firstInstall = firstInstall;
        this.noUserAction = noUserAction;
        this.saveToDownloads = saveToDownloads;
        this.deleteAfterInstall = deleteAfterInstall;
        this.customInstallerPackage = customInstallerPackage == null ? "" : customInstallerPackage.trim();
        this.iconUrl = iconUrl == null ? "" : iconUrl.trim();
    }

    public String getInstallerMode() { return installerMode; }
    public String getPackageName() { return packageName; }
    public String getDisplayName() { return displayName; }
    public String getVersionName() { return versionName; }
    public long getVersionCode() { return versionCode; }
    public boolean isFirstInstall() { return firstInstall; }
    public boolean isNoUserAction() { return noUserAction; }
    public boolean isSaveToDownloads() { return saveToDownloads; }
    public boolean isDeleteAfterInstall() { return deleteAfterInstall; }
    public String getCustomInstallerPackage() { return customInstallerPackage; }
    public String getIconUrl() { return iconUrl; }
}
