package vt.async.await;

/**
 * Exception thrown when an asynchronous task fails with a checked exception.
 */
public final class AsyncTaskExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause the underlying cause
     */
    public AsyncTaskExecutionException(Throwable cause) {
        super(cause != null ? cause.getMessage() : null, cause);
    }

    /**
     * Constructs a new exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public AsyncTaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
