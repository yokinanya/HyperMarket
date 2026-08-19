package com.github.sisong;

public final class sfpatcher {
    private static final sfpatcher INSTANCE = new sfpatcher();
    private static boolean loaded;
    private static String loadError = "";

    static {
        try {
            System.loadLibrary("patcherV3");
            nativeInit();
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            loadError = error.getMessage() == null ? "native library unavailable" : error.getMessage();
        }
    }

    private sfpatcher() { }

    public static sfpatcher getInstance() { return INSTANCE; }

    public int patch(String patchPath, String oldPath, String outputPath) {
        if (!loaded) throw new IllegalStateException("Delta patch engine is unavailable: " + loadError);
        return nativePatch(patchPath, null, 0L, new TByteBuf(), oldPath, null, 0L,
                new TByteBuf(), outputPath, false, 6, null, 0L,
                new EmptyDiffInfoListener(), new TDiffInfo(), null, 0, null, 0);
    }

    private static native void nativeInit();

    private static native int nativePatch(String patchPath, IReadStream patchStream,
            long patchSize, TByteBuf patchBuffer, String oldPath, IReadStream oldStream,
            long oldSize, TByteBuf oldBuffer, String outputPath, boolean decompress,
            int threadCount, String cachePath, long maxMemory, IDiffInfoListener listener,
            TDiffInfo diffInfo, long[] ranges, int rangeCount, byte[] rangeData, int rangeDataLength);

    public interface IReadStream {
        long getStreamSize();
        boolean readStreamData(long position, TByteBuf buffer, int size);
    }

    public interface IDiffInfoListener {
        int diffInfo(TDiffInfo info);
    }

    public static final class EmptyDiffInfoListener implements IDiffInfoListener {
        @Override public int diffInfo(TDiffInfo info) { return 0; }
    }

    public static final class TByteBuf {
        public long cBufHandle;
    }

    public static final class TDiffInfo {
        public long maxUncompressMemory;
        public int threadNum;
    }
}
