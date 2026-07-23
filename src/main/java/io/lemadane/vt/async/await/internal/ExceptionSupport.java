package io.lemadane.vt.async.await.internal;

import io.lemadane.vt.async.await.TaskExecutionException;

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
            Thread.currentThread().interrupt();
            CancellationException cancellationException = new CancellationException("Operation was interrupted");
            cancellationException.initCause(interruptedException);
            throw cancellationException;
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
     * @return CancellationException with interrupted cause attached
     */
    public static CancellationException handleInterrupted(InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        CancellationException cancellationException = new CancellationException("Awaiting thread was interrupted");
        cancellationException.initCause(interruptedException);
        return cancellationException;
    }
}
