package com.example.fileminer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ✅ ScanExecutor
 * Runs scanning tasks in background without freezing UI.
 */
public class ScanExecutor {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<?> runningTask;

    public void run(Runnable runnable) {
        cancel(); // ✅ Cancel old scan if running
        runningTask = executorService.submit(runnable);
    }

    public void cancel() {
        if (runningTask != null && !runningTask.isDone()) {
            runningTask.cancel(true);
        }
    }

    public void shutdown() {
        cancel();
        executorService.shutdownNow();
    }
}
