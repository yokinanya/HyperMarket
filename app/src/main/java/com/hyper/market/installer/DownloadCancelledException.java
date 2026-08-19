package com.hyper.market.installer;

import java.io.IOException;

public final class DownloadCancelledException extends IOException {
    public DownloadCancelledException(String message) {
        super(message);
    }

    public DownloadCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
