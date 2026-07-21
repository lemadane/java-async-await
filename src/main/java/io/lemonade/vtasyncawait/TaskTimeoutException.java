package io.lemonade.vtasyncawait;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Exception thrown when an await operation times out before task completion.
 */
public final class TaskTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String taskName;
    private final Duration timeout;

    /**
     * Constructs a TaskTimeoutException.
     *
     * @param taskName the name of the task that timed out
     * @param timeout the requested timeout duration
     * @param cause the underlying TimeoutException
     */
    public TaskTimeoutException(String taskName, Duration timeout, TimeoutException cause) {
        super("Task '" + (taskName != null ? taskName : "anonymous") + "' timed out after " + timeout, cause);
        this.taskName = taskName != null ? taskName : "anonymous";
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Returns the task name.
     *
     * @return the task name
     */
    public String taskName() {
        return taskName;
    }

    /**
     * Returns the requested timeout duration.
     *
     * @return the timeout duration
     */
    public Duration timeout() {
        return timeout;
    }
}
