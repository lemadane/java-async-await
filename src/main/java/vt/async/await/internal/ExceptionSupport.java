package vt.async.await.internal;

import vt.async.await.AsyncTaskExecutionException;
import vt.async.await.AsyncTaskInterruptedException;

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
            throw new AsyncTaskExecutionException("Child task was interrupted", interruptedException);
        }
        if (cause instanceof CancellationException cancellationException) {
            throw cancellationException;
        }

        throw new AsyncTaskExecutionException(cause);
    }

    /**
     * Handles an InterruptedException during await.
     *
     * @param interruptedException the exception
     * @return AsyncTaskInterruptedException with interrupted cause attached
     */
    public static AsyncTaskInterruptedException handleInterrupted(InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        return new AsyncTaskInterruptedException("Awaiting thread was interrupted", interruptedException);
    }
}
