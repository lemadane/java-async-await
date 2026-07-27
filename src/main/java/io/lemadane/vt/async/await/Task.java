package io.lemadane.vt.async.await;

import io.lemadane.vt.async.await.internal.ExceptionSupport;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
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
    private final ManagedFutureTask<T> futureTask;
    private volatile Thread executingThread;
    private final Runnable startAction;

    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> completionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.locks.ReentrantLock stateLock = new java.util.concurrent.locks.ReentrantLock();
    private final java.util.concurrent.atomic.AtomicBoolean completionPublished = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile State state = State.CREATED;
    private volatile long ownerScopeId = -1L;

    void setOwnerScopeId(long ownerScopeId) {
        this.ownerScopeId = ownerScopeId;
    }

    long ownerScopeId() {
        return ownerScopeId;
    }

    Task(String name, Callable<T> callable, Thread executingThread, java.util.function.Function<Task<T>, Runnable> startActionFactory) {
        this.name = name != null && !name.isBlank() ? name : "anonymous";
        this.futureTask = new ManagedFutureTask<>(callable, this);
        this.executingThread = executingThread;
        this.startAction = Objects.requireNonNull(startActionFactory.apply(this), "startAction");
    }

    void setExecutingThread(Thread thread) {
        this.executingThread = thread;
    }

    ManagedFutureTask<T> futureTask() {
        return futureTask;
    }

    private void notifyListeners() {
        java.util.List<Runnable> copy;
        stateLock.lock();
        try {
            copy = new java.util.ArrayList<>(completionListeners);
            completionListeners.clear();
        } finally {
            stateLock.unlock();
        }

        for (Runnable listener : copy) {
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
        if (futureTask.isDone() && !completionPublished.get()) {
            completeFromFuture(futureTask);
        }

        boolean runImmediately = false;
        stateLock.lock();
        try {
            if (completionPublished.get()) {
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
            if (state == State.CREATED) {
                return State.CREATED;
            }
            if (futureTask.isCancelled()) {
                return State.CANCELLED;
            }
            if (futureTask.isDone()) {
                try {
                    Future.State fState = futureTask.state();
                    if (fState == Future.State.SUCCESS) {
                        return State.SUCCESS;
                    } else if (fState == Future.State.FAILED) {
                        return State.FAILED;
                    } else if (fState == Future.State.CANCELLED) {
                        return State.CANCELLED;
                    }
                } catch (Exception e) {
                    return State.FAILED;
                }
            }
            return state;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public java.util.concurrent.Future.State state() {
        stateLock.lock();
        try {
            if (state == State.CREATED) {
                return java.util.concurrent.Future.State.RUNNING;
            }
            if (futureTask.isCancelled()) {
                return java.util.concurrent.Future.State.CANCELLED;
            }
            if (futureTask.isDone()) {
                return futureTask.state();
            }
            return java.util.concurrent.Future.State.RUNNING;
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

    void completeFromFuture(ManagedFutureTask<T> future) {
        State terminalState = determineTerminalState(future);

        boolean transitioned = false;
        stateLock.lock();
        try {
            if (state == State.CREATED || state == State.RUNNING) {
                state = terminalState;
                transitioned = true;
                completionPublished.set(true);
            }
        } finally {
            stateLock.unlock();
        }

        if (transitioned) {
            notifyListeners();
        }
    }

    private State determineTerminalState(ManagedFutureTask<T> future) {
        if (future.isCancelled()) {
            return State.CANCELLED;
        }
        try {
            Future.State fState = future.state();
            if (fState == Future.State.SUCCESS) {
                return State.SUCCESS;
            } else if (fState == Future.State.FAILED) {
                return State.FAILED;
            } else if (fState == Future.State.CANCELLED) {
                return State.CANCELLED;
            }
        } catch (Exception e) {
            return State.FAILED;
        }
        return State.FAILED;
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
        stateLock.lock();
        try {
            if (state == State.SUCCESS || state == State.FAILED || state == State.CANCELLED) {
                return false;
            }
        } finally {
            stateLock.unlock();
        }
        return futureTask.cancel(mayInterruptIfRunning);
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

    static final class ManagedFutureTask<T> extends FutureTask<T> {
        private final Task<T> owner;

        ManagedFutureTask(Callable<T> callable, Task<T> owner) {
            super(callable);
            this.owner = owner;
        }

        @Override
        protected void done() {
            owner.completeFromFuture(this);
        }
    }
}
