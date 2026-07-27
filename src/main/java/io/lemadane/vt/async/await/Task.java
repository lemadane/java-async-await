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

    /**
     * Internal lifecycle states of a task.
     */
    public enum State {
        /** Task has been created but not yet started. */
        CREATED,
        /** Task is currently running. */
        RUNNING,
        /** Task finished successfully. */
        SUCCESS,
        /** Task finished with an error. */
        FAILED,
        /** Task was cancelled before or during execution. */
        CANCELLED
    }

    private final String name;
    private final FutureTask<T> futureTask;
    private volatile Thread executingThread;
    private final Runnable startAction;

    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> completionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.locks.ReentrantLock stateLock = new java.util.concurrent.locks.ReentrantLock();
    private State state = State.CREATED;

    Task(String name, FutureTask<T> futureTask, Thread executingThread, Runnable startAction) {
        this.name = name != null && !name.isBlank() ? name : "anonymous";
        this.futureTask = Objects.requireNonNull(futureTask, "futureTask");
        this.executingThread = executingThread;
        this.startAction = Objects.requireNonNull(startAction, "startAction");
    }

    void setExecutingThread(Thread thread) {
        this.executingThread = thread;
    }

    private void notifyListeners() {
        for (Runnable listener : completionListeners) {
            try {
                listener.run();
            } catch (Throwable t) {
                // Ignore listener exceptions to avoid propagating to executor thread
            }
        }
    }

    /**
     * Registers a callback listener to be executed when the task completes.
     * If the task is already completed, the listener is run immediately on the caller thread.
     *
     * @param listener the listener callback
     */
    public void onComplete(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        boolean runImmediately = false;
        stateLock.lock();
        try {
            if (state == State.SUCCESS || state == State.FAILED || state == State.CANCELLED) {
                runImmediately = true;
            } else {
                completionListeners.add(listener);
            }
        } finally {
            stateLock.unlock();
        }
        if (runImmediately) {
            try {
                listener.run();
            } catch (Throwable t) {
                // Ignore listener exceptions
            }
        }
    }

    /**
     * Returns the current lifecycle state of this task.
     *
     * @return the task state
     */
    public State lifecycleState() {
        stateLock.lock();
        try {
            return state;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public java.util.concurrent.Future.State state() {
        stateLock.lock();
        try {
            switch (state) {
                case CREATED:
                case RUNNING:
                    return java.util.concurrent.Future.State.RUNNING;
                case SUCCESS:
                    return java.util.concurrent.Future.State.SUCCESS;
                case FAILED:
                    return java.util.concurrent.Future.State.FAILED;
                case CANCELLED:
                    return java.util.concurrent.Future.State.CANCELLED;
                default:
                    throw new IllegalStateException("Unknown state: " + state);
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Starts execution of the task's thread if it is in the CREATED state.
     */
    void start() {
        stateLock.lock();
        try {
            if (state == State.CREATED) {
                state = State.RUNNING;
                startAction.run();
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transitions the task to SUCCESS state.
     */
    void transitionToSuccess() {
        stateLock.lock();
        try {
            if (state == State.RUNNING) {
                state = State.SUCCESS;
            }
        } finally {
            stateLock.unlock();
        }
        notifyListeners();
    }

    /**
     * Transitions the task to FAILED state.
     */
    void transitionToFailed() {
        stateLock.lock();
        try {
            if (state == State.RUNNING) {
                state = State.FAILED;
            }
        } finally {
            stateLock.unlock();
        }
        notifyListeners();
    }

    /**
     * Returns the underlying thread executing this task.
     *
     * @return the executing thread
     */
    Thread executingThread() {
        return executingThread;
    }

    /**
     * Awaits completion of this task and returns its result.
     *
     * @return the result of the task
     * @throws RuntimeException if the task threw an unchecked exception or was cancelled
     * @throws TaskExecutionException if the task threw a checked exception
     * @throws TaskInterruptedException if the awaiting thread was interrupted
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
     * @throws TaskInterruptedException if the awaiting thread was interrupted
     */
    public T await(Duration timeout) {
        long nanos = toNanosSafe(timeout);
        try {
            return futureTask.get(nanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw ExceptionSupport.handleInterrupted(e);
        } catch (ExecutionException e) {
            throw ExceptionSupport.unwrapAndRethrow(e);
        } catch (TimeoutException e) {
            throw new TaskTimeoutException(name, timeout, e);
        }
    }

    private static long toNanosSafe(Duration duration) {
        Objects.requireNonNull(duration, "timeout duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Timeout duration must not be negative: " + duration);
        }
        if (duration.isZero()) {
            return 0L;
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Cancels execution of this task, interrupting its virtual thread if running.
     *
     * @return {@code true} if the task transitioned to CANCELLED state successfully
     */
    public boolean cancel() {
        return cancel(true);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = false;
        stateLock.lock();
        try {
            if (state != State.SUCCESS && state != State.FAILED && state != State.CANCELLED) {
                state = State.CANCELLED;
                futureTask.cancel(mayInterruptIfRunning);
                Thread thread = executingThread;
                if (mayInterruptIfRunning && thread != null && thread.isAlive()) {
                    thread.interrupt();
                }
                cancelled = true;
            }
        } finally {
            stateLock.unlock();
        }
        if (cancelled) {
            notifyListeners();
        }
        return cancelled;
    }

    @Override
    public boolean isCancelled() {
        stateLock.lock();
        try {
            return state == State.CANCELLED;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public boolean isDone() {
        stateLock.lock();
        try {
            return state == State.SUCCESS || state == State.FAILED || state == State.CANCELLED;
        } finally {
            stateLock.unlock();
        }
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
        stateLock.lock();
        try {
            if (state != State.RUNNING) {
                return false;
            }
            Thread thread = executingThread;
            return thread != null && thread.isAlive();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns whether the underlying executing thread is a virtual thread.
     *
     * @return {@code true} if virtual thread
     */
    public boolean isVirtualThread() {
        Thread thread = executingThread;
        return thread != null && thread.isVirtual();
    }
}
