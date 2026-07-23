package io.lemadane.vt.async.await.internal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal launcher helper for starting virtual threads.
 */
public final class VirtualThreadLauncher {

    private final String threadNamePrefix;
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private final AtomicLong sequence = new AtomicLong();

    public VirtualThreadLauncher(String threadNamePrefix, Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.threadNamePrefix = Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        this.uncaughtExceptionHandler = uncaughtExceptionHandler != null ? uncaughtExceptionHandler : (t, e) -> {};
    }

    public Thread launch(String logicalName, Runnable task) {
        Objects.requireNonNull(task, "task");
        long index = sequence.incrementAndGet();
        String threadName;
        if (logicalName != null && !logicalName.isBlank()) {
            threadName = threadNamePrefix + logicalName + "-" + index;
        } else {
            threadName = threadNamePrefix + index;
        }

        Thread thread = Thread.ofVirtual()
                .name(threadName)
                .uncaughtExceptionHandler(uncaughtExceptionHandler)
                .unstarted(task);

        thread.start();
        return thread;
    }
}
