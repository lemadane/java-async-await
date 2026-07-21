package io.lemonade.vtasyncawait;

/**
 * Exception thrown when an asynchronous task fails with a checked exception.
 */
public final class TaskExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause the underlying cause
     */
    public TaskExecutionException(Throwable cause) {
        super(cause != null ? cause.getMessage() : null, cause);
    }

    /**
     * Constructs a new exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public TaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
