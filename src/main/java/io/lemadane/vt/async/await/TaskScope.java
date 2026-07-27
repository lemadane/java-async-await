package io.lemadane.vt.async.await;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Structured scope for managing child task lifetimes without preview APIs.
 *
 * <p>Ensures thread-safe registration and idempotent cleanup of tasks.
 */
public final class TaskScope implements AutoCloseable {

    private enum ScopeState {
        OPEN,
        CLOSING,
        CLOSED
    }

    private final AsyncRuntime runtime;
    private final Set<Task<?>> tasks = ConcurrentHashMap.newKeySet();
    private final ReentrantLock lock = new ReentrantLock();
    private ScopeState state = ScopeState.OPEN;

    TaskScope(AsyncRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Submits a callable task bound to this scope.
     *
     * @param <T> the result type
     * @param operation the operation
     * @return the created Task
     * @throws IllegalStateException if scope is closed or closing
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
     * @throws IllegalStateException if scope is closed or closing
     */
    public <T> Task<T> async(String taskName, Callable<? extends T> operation) {
        Objects.requireNonNull(operation, "operation");
        Task<T> task;
        lock.lock();
        try {
            if (state != ScopeState.OPEN) {
                throw new IllegalStateException("TaskScope is not open (state: " + state + ")");
            }
            task = runtime.createUnstartedTask(taskName, operation);
            tasks.add(task);
        } finally {
            lock.unlock();
        }

        try {
            task.start();
        } catch (Throwable t) {
            lock.lock();
            try {
                tasks.remove(task);
            } finally {
                lock.unlock();
            }
            throw t;
        }
        return task;
    }

    /**
     * Submits a runnable task bound to this scope.
     *
     * @param operation the operation
     * @return the created Task
     * @throws IllegalStateException if scope is closed or closing
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
     * @throws IllegalStateException if scope is closed or closing
     */
    public Task<Void> async(String taskName, Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        return async(taskName, () -> {
            operation.run();
            return null;
        });
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
        try {
            return runtime.await(task);
        } finally {
            if (task.isDone()) {
                tasks.remove(task);
            }
        }
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
        try {
            return runtime.await(task, timeout);
        } finally {
            if (task.isDone()) {
                tasks.remove(task);
            }
        }
    }

    /**
     * Awaits a task created by this scope up to the specified timeout, and cancels the task if it times out.
     *
     * @param <T> the result type
     * @param task the task to await
     * @param timeout maximum duration to wait
     * @return the result of the task
     * @throws IllegalArgumentException if task was not created by this scope
     */
    public <T> T awaitAndCancel(Task<T> task, Duration timeout) {
        validateTaskOwner(task);
        try {
            return runtime.awaitAndCancel(task, timeout);
        } finally {
            if (task.isDone()) {
                tasks.remove(task);
            }
        }
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
        lock.lock();
        try {
            return state == ScopeState.CLOSED;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        Set<Task<?>> tasksToCancel;
        lock.lock();
        try {
            if (state != ScopeState.OPEN) {
                return;
            }
            state = ScopeState.CLOSING;
            tasksToCancel = new HashSet<>(tasks);
        } finally {
            lock.unlock();
        }

        Throwable primaryException = null;

        // Cancel all unfinished tasks
        for (Task<?> task : tasksToCancel) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }

        // Ensure all child tasks are completed/awaited to avoid leaks
        for (Task<?> task : tasksToCancel) {
            // Prevent deadlocking if close is called from a child task within the scope
            if (task.executingThread() == Thread.currentThread()) {
                continue;
            }
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

        lock.lock();
        try {
            state = ScopeState.CLOSED;
            tasks.clear();
        } finally {
            lock.unlock();
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

    /**
     * Awaits all tasks. If any task fails or is cancelled, it immediately fails-fast
     * and cancels all other tasks in the collection.
     *
     * @param <T> the task result type
     * @param inputTasks the tasks collection
     * @return the list of results in the same order as the inputs
     * @throws IllegalArgumentException if any task was not created by this scope
     */
    public <T> List<T> all(Collection<Task<? extends T>> inputTasks) {
        verifyScopeOwnership(inputTasks);
        try {
            return runtime.all(inputTasks);
        } finally {
            cleanSettledTasks(inputTasks);
        }
    }

    /**
     * Returns the result of the first task that succeeds. If all tasks fail,
     * throws an {@link AggregateException} containing all failures.
     *
     * @param <T> the task result type
     * @param inputTasks the tasks collection
     * @return the first successful result
     * @throws IllegalArgumentException if any task was not created by this scope
     */
    public <T> T any(Collection<Task<? extends T>> inputTasks) {
        verifyScopeOwnership(inputTasks);
        try {
            return runtime.any(inputTasks);
        } finally {
            cleanSettledTasks(inputTasks);
        }
    }

    /**
     * Returns the result/exception of the first task that completes (succeeds,
     * fails, or cancels), and cancels all other tasks.
     *
     * @param <T> the task result type
     * @param inputTasks the tasks collection
     * @return the result of the first completed task
     * @throws IllegalArgumentException if any task was not created by this scope
     */
    public <T> T race(Collection<Task<? extends T>> inputTasks) {
        verifyScopeOwnership(inputTasks);
        try {
            return runtime.race(inputTasks);
        } finally {
            cleanSettledTasks(inputTasks);
        }
    }

    /**
     * Awaits all tasks to settle (succeed, fail, or cancel) without throwing exceptions,
     * returning the inputs.
     *
     * @param <T> the task result type
     * @param inputTasks the tasks collection
     * @return the input collection of tasks after all have completed
     * @throws IllegalArgumentException if any task was not created by this scope
     */
    public <T> Collection<Task<? extends T>> allSettled(Collection<Task<? extends T>> inputTasks) {
        verifyScopeOwnership(inputTasks);
        try {
            return runtime.allSettled(inputTasks);
        } finally {
            cleanSettledTasks(inputTasks);
        }
    }

    private void verifyScopeOwnership(Collection<? extends Task<?>> inputTasks) {
        if (inputTasks == null) {
            return;
        }
        for (Task<?> task : inputTasks) {
            validateTaskOwner(task);
        }
    }

    private void cleanSettledTasks(Collection<? extends Task<?>> inputTasks) {
        if (inputTasks == null) {
            return;
        }
        for (Task<?> task : inputTasks) {
            if (task.isDone()) {
                tasks.remove(task);
            }
        }
    }

    private void validateTaskOwner(Task<?> task) {
        Objects.requireNonNull(task, "task");
        if (!tasks.contains(task)) {
            throw new IllegalArgumentException("Task was not created by this scope: " + task.name());
        }
    }
}
