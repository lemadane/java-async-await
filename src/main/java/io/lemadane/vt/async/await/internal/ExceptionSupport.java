package io.lemadane.vt.async.await.internal;

import io.lemadane.vt.async.await.TaskExecutionException;
import io.lemadane.vt.async.await.TaskInterruptedException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Internal exception handling and unwrapping utilities.
 */
public final class ExceptionSupport {

    private ExceptionSupport() {
    }

    /**
     * Unwraps and rethrows the root cause of an ExecutionException according to standard exception semantics.
     *
     * @param exception the ExecutionException
     * @return a RuntimeException if not rethrown directly
     */
    public static RuntimeException unwrapAndRethrow(ExecutionException exception) {
        Throwable cause = exception.getCause();
        if (cause == null) {
            cause = exception;
        }

        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof InterruptedException interruptedException) {
            throw new TaskExecutionException("Child task was interrupted", interruptedException);
        }
        if (cause instanceof CancellationException cancellationException) {
            throw cancellationException;
        }

        throw new TaskExecutionException(cause);
    }

    /**
     * Handles an InterruptedException during await.
     *
     * @param interruptedException the exception
     * @return TaskInterruptedException with interrupted cause attached
     */
    public static TaskInterruptedException handleInterrupted(InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        return new TaskInterruptedException("Awaiting thread was interrupted", interruptedException);
    }
}
