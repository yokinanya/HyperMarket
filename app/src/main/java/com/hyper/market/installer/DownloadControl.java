package com.hyper.market.installer;

public final class DownloadControl {
    private final Object lock = new Object();
    private volatile boolean paused;
    private volatile boolean cancelled;

    public void pause() {
        paused = true;
    }

    public void resume() {
        synchronized (lock) {
            paused = false;
            lock.notifyAll();
        }
    }

    public void cancel() {
        synchronized (lock) {
            cancelled = true;
            paused = false;
            lock.notifyAll();
        }
    }

    public boolean isPaused() { return paused; }
    public boolean isCancelled() { return cancelled; }

    public void awaitIfPaused() throws DownloadCancelledException {
        synchronized (lock) {
            while (paused && !cancelled) {
                try {
                    lock.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new DownloadCancelledException("下载线程被中断", exception);
                }
            }
        }
        throwIfCancelled();
    }

    public void throwIfCancelled() throws DownloadCancelledException {
        if (cancelled) throw new DownloadCancelledException("下载已取消");
    }

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }
}
