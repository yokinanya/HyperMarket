package com.hyper.market.model;

public final class ApkArtifact {
    private final String name;
    private final String type;
    private final String url;
    private final long size;
    private final String hash;
    private final String diffUrl;
    private final long diffSize;
    private final String diffHash;
    private final int diffVersion;
    private final String diffBasePath;

    public ApkArtifact(
            String name,
            String type,
            String url,
            long size,
            String hash,
            String diffUrl,
            long diffSize,
            String diffHash) {
        this(name, type, url, size, hash, diffUrl, diffSize, diffHash, 3, "");
    }

    public ApkArtifact(
            String name,
            String type,
            String url,
            long size,
            String hash,
            String diffUrl,
            long diffSize,
            String diffHash,
            int diffVersion,
            String diffBasePath) {
        this.name = name;
        this.type = type;
        this.url = url;
        this.size = size;
        this.hash = hash;
        this.diffUrl = diffUrl;
        this.diffSize = diffSize;
        this.diffHash = diffHash;
        this.diffVersion = diffVersion;
        this.diffBasePath = diffBasePath;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getUrl() { return url; }
    public long getSize() { return size; }
    public String getHash() { return hash; }
    public String getDiffUrl() { return diffUrl; }
    public long getDiffSize() { return diffSize; }
    public String getDiffHash() { return diffHash; }
    public int getDiffVersion() { return diffVersion; }
    public String getDiffBasePath() { return diffBasePath; }

    public boolean hasDelta() {
        return !diffUrl.isEmpty() && diffSize > 0;
    }
}
