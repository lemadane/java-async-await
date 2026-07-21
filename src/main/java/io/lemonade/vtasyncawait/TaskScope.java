package io.lemonade.vtasyncawait;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Structured scope for managing child task lifetimes without preview APIs.
 */
public final class TaskScope implements AutoCloseable {

    private final AsyncRuntime runtime;
    private final Set<Task<?>> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    TaskScope(AsyncRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Submits a callable task bound to this scope.
     *
     * @param <T> the result type
     * @param operation the operation
     * @return the created Task
     * @throws IllegalStateException if scope is closed
     */
    public <T> Task<T> async(Callable<? extends T> operation) {
        return async(null, operation);
    }

    /**
     * Submits a named callable task bound to this scope.
     *
     * @param <T> the result type
     * @param taskName logical task name
     * @param operation the operation
     * @return the created Task
     * @throws IllegalStateException if scope is closed
     */
    public <T> Task<T> async(String taskName, Callable<? extends T> operation) {
        ensureOpen();
        Task<T> task = runtime.async(taskName, operation);
        tasks.add(task);
        return task;
    }

    /**
     * Submits a runnable task bound to this scope.
     *
     * @param operation the operation
     * @return the created Task
     * @throws IllegalStateException if scope is closed
     */
    public Task<Void> async(Runnable operation) {
        return async(null, operation);
    }

    /**
     * Submits a named runnable task bound to this scope.
     *
     * @param taskName logical task name
     * @param operation the operation
     * @return the created Task
     * @throws IllegalStateException if scope is closed
     */
    public Task<Void> async(String taskName, Runnable operation) {
        ensureOpen();
        Task<Void> task = runtime.async(taskName, operation);
        tasks.add(task);
        return task;
    }

    /**
     * Awaits a task created by this scope.
     *
     * @param <T> the result type
     * @param task the task to await
     * @return the result
     * @throws IllegalArgumentException if task was not created by this scope
     */
    public <T> T await(Task<T> task) {
        validateTaskOwner(task);
        return runtime.await(task);
    }

    /**
     * Awaits a task created by this scope with a timeout.
     *
     * @param <T> the result type
     * @param task the task to await
     * @param timeout the timeout
     * @return the result
     * @throws IllegalArgumentException if task was not created by this scope
     */
    public <T> T await(Task<T> task, Duration timeout) {
        validateTaskOwner(task);
        return runtime.await(task, timeout);
    }

    /**
     * Cancels all unfinished child tasks registered with this scope.
     */
    public void cancel() {
        for (Task<?> task : tasks) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
    }

    /**
     * Returns whether this scope is closed.
     *
     * @return {@code true} if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        Throwable primaryException = null;

        // Cancel all unfinished tasks
        cancel();

        // Ensure all child tasks are completed/awaited to avoid leaks
        for (Task<?> task : tasks) {
            try {
                task.await();
            } catch (CancellationException ce) {
                if (!task.isCancelled()) {
                    if (primaryException == null) {
                        primaryException = ce;
                    } else if (primaryException != ce) {
                        primaryException.addSuppressed(ce);
                    }
                }
            } catch (Throwable t) {
                if (primaryException == null) {
                    primaryException = t;
                } else if (primaryException != t) {
                    primaryException.addSuppressed(t);
                }
            }
        }

        if (primaryException != null) {
            if (primaryException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (primaryException instanceof Error error) {
                throw error;
            }
            throw new TaskExecutionException("Error closing task scope", primaryException);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("TaskScope is closed");
        }
    }

    private void validateTaskOwner(Task<?> task) {
        Objects.requireNonNull(task, "task");
        if (!tasks.contains(task)) {
            throw new IllegalArgumentException("Task was not created by this scope: " + task.name());
        }
    }
}
