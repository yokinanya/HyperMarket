package com.hyper.market.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class ParallelFileDownloader {
    static final long MIN_PARALLEL_SIZE = 8L * 1024 * 1024;
    private static final int THREAD_COUNT = 4;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int BUFFER_SIZE = 64 * 1024;
    private final Map<String, String> headers;

    ParallelFileDownloader(Map<String, String> headers) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    boolean tryDownload(File target, String url, long size, DownloadControl control,
                        DownloadControl.ProgressListener listener) throws IOException {
        if (size < MIN_PARALLEL_SIZE || target.isFile()) return false;
        List<Segment> segments = segments(target, size);
        if (!hasPartialSegment(segments) && !supportsRanges(url)) return false;
        ProgressReporter reporter = new ProgressReporter(segments, size, listener);
        while (!allComplete(segments)) {
            control.awaitIfPaused();
            downloadRound(url, segments, control, reporter);
        }
        merge(target, segments);
        return true;
    }

    private boolean supportsRanges(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try {
            connection.setRequestProperty("Range", "bytes=0-0");
            return connection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL;
        } finally {
            connection.disconnect();
        }
    }

    private void downloadRound(String url, List<Segment> segments, DownloadControl control,
                               ProgressReporter reporter) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Segment segment : segments) {
                if (!segment.complete()) {
                    futures.add(executor.submit(() -> {
                        downloadSegment(url, segment, control, reporter);
                        return null;
                    }));
                }
            }
            await(futures);
        } finally {
            executor.shutdownNow();
        }
    }

    private void downloadSegment(String url, Segment segment, DownloadControl control,
                                 ProgressReporter reporter) throws IOException {
        long offset = segment.downloaded();
        if (offset > segment.length()) {
            throw new IOException("下载分片长度异常：" + segment.file);
        }
        if (offset == segment.length()) return;
        HttpURLConnection connection = open(url);
        try {
            long requestStart = segment.start + offset;
            connection.setRequestProperty("Range", "bytes=" + requestStart + "-" + segment.end);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("服务器未返回分段下载响应：HTTP " + status);
            }
            writeSegment(connection.getInputStream(), segment.file, offset, control, reporter);
        } finally {
            connection.disconnect();
        }
    }

    private void writeSegment(InputStream input, File file, long offset, DownloadControl control,
                              ProgressReporter reporter) throws IOException {
        try (InputStream source = input;
             FileOutputStream output = new FileOutputStream(file, offset > 0)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                control.throwIfCancelled();
                if (control.isPaused()) return;
                int count = source.read(buffer);
                if (count == -1) return;
                output.write(buffer, 0, count);
                reporter.add(count);
            }
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        return connection;
    }

    private void await(List<Future<?>> futures) throws IOException {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("分段下载线程被中断", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof IOException ioException) throw ioException;
                throw new IOException("分段下载失败", cause);
            }
        }
    }

    private void merge(File target, List<Segment> segments) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (Segment segment : segments) {
                requireComplete(segment);
                try (InputStream input = new FileInputStream(segment.file)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
            }
        }
        for (Segment segment : segments) {
            if (!segment.file.delete()) throw new IOException("无法删除下载分片：" + segment.file);
        }
    }

    private void requireComplete(Segment segment) throws IOException {
        if (!segment.complete()) {
            throw new IOException("下载分片不完整：" + segment.file.length() + "/" + segment.length());
        }
    }

    private List<Segment> segments(File target, long size) {
        long baseLength = size / THREAD_COUNT;
        List<Segment> result = new ArrayList<>(THREAD_COUNT);
        for (int index = 0; index < THREAD_COUNT; index++) {
            long start = index * baseLength;
            long end = index == THREAD_COUNT - 1 ? size - 1 : start + baseLength - 1;
            result.add(new Segment(new File(target + ".segment-" + index), start, end));
        }
        return result;
    }

    private boolean hasPartialSegment(List<Segment> segments) {
        return segments.stream().anyMatch(segment -> segment.file.isFile());
    }

    private boolean allComplete(List<Segment> segments) {
        return segments.stream().allMatch(Segment::complete);
    }

    private static final class Segment {
        private final File file;
        private final long start;
        private final long end;

        private Segment(File file, long start, long end) {
            this.file = file;
            this.start = start;
            this.end = end;
        }

        private long length() { return end - start + 1; }
        private long downloaded() { return file.isFile() ? file.length() : 0; }
        private boolean complete() { return downloaded() == length(); }
    }

    private static final class ProgressReporter {
        private final AtomicLong downloaded;
        private final AtomicInteger percentage = new AtomicInteger(-1);
        private final long total;
        private final DownloadControl.ProgressListener listener;

        private ProgressReporter(List<Segment> segments, long total,
                                 DownloadControl.ProgressListener listener) {
            this.downloaded = new AtomicLong(segments.stream().mapToLong(Segment::downloaded).sum());
            this.total = total;
            this.listener = listener;
        }

        private void add(int count) {
            long current = downloaded.addAndGet(count);
            int next = (int) (current * 100 / total);
            int previous = percentage.getAndSet(next);
            if (listener != null && next != previous) listener.onProgress(current, total);
        }
    }
}
