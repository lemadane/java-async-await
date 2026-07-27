package io.lemadane.vt.async.await;

/**
 * Exception thrown when the thread awaiting a task is interrupted.
 */
public final class TaskInterruptedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the underlying InterruptedException
     */
    public TaskInterruptedException(String message, InterruptedException cause) {
        super(message, cause);
    }
}
