package io.lemadane.vt.async.await;

import io.lemadane.vt.async.await.internal.ExceptionSupport;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Represents an asynchronous operation running on a virtual thread.
 *
 * @param <T> the result type
 */
public final class Task<T> implements Future<T> {

    private final String name;
    private final FutureTask<T> futureTask;
    private final Thread executingThread;

    Task(String name, FutureTask<T> futureTask, Thread executingThread) {
        this.name = name != null && !name.isBlank() ? name : "anonymous";
        this.futureTask = Objects.requireNonNull(futureTask, "futureTask");
        this.executingThread = executingThread;
    }

    /**
     * Awaits completion of this task and returns its result.
     *
     * @return the result of the task
     * @throws RuntimeException if the task threw an unchecked exception or was cancelled
     * @throws TaskExecutionException if the task threw a checked exception
     */
    public T await() {
        try {
            return futureTask.get();
        } catch (InterruptedException e) {
            throw ExceptionSupport.handleInterrupted(e);
        } catch (ExecutionException e) {
            throw ExceptionSupport.unwrapAndRethrow(e);
        }
    }

    /**
     * Awaits completion of this task up to the specified timeout duration.
     *
     * @param timeout the maximum duration to wait
     * @return the result of the task
     * @throws TaskTimeoutException if the task did not complete within the timeout
     * @throws RuntimeException if the task threw an unchecked exception or was cancelled
     * @throws TaskExecutionException if the task threw a checked exception
     */
    public T await(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        try {
            return futureTask.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw ExceptionSupport.handleInterrupted(e);
        } catch (ExecutionException e) {
            throw ExceptionSupport.unwrapAndRethrow(e);
        } catch (TimeoutException e) {
            throw new TaskTimeoutException(name, timeout, e);
        }
    }

    /**
     * Cancels execution of this task, interrupting its virtual thread if running.
     *
     * @return {@code true} if the task was cancelled
     */
    public boolean cancel() {
        return cancel(true);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean result = futureTask.cancel(mayInterruptIfRunning);
        if (mayInterruptIfRunning && executingThread != null && executingThread.isAlive()) {
            executingThread.interrupt();
        }
        return result;
    }

    @Override
    public boolean isCancelled() {
        return futureTask.isCancelled();
    }

    @Override
    public boolean isDone() {
        return futureTask.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return futureTask.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return futureTask.get(timeout, unit);
    }

    /**
     * Returns the name of this task.
     *
     * @return the task name
     */
    public String name() {
        return name;
    }

    /**
     * Returns whether the task is currently running.
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        return !isDone() && !isCancelled() && executingThread != null && executingThread.isAlive();
    }

    /**
     * Returns whether the underlying executing thread is a virtual thread.
     *
     * @return {@code true} if virtual thread
     */
    public boolean isVirtualThread() {
        return executingThread != null && executingThread.isVirtual();
    }
}
