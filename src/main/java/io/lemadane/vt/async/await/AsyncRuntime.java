package io.lemadane.vt.async.await;

import io.lemadane.vt.async.await.internal.VirtualThreadLauncher;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Configurable, injectable runtime for launching and awaiting virtual thread tasks.
 *
 * <p>Implements {@link AutoCloseable} to clean up resources if owning a caller-provided executor.
 */
public final class AsyncRuntime implements AutoCloseable {

    private final String threadNamePrefix;
    private final TaskDecorator taskDecorator;
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private final VirtualThreadLauncher launcher;
    private final ExecutorService executorService;
    private final boolean ownsExecutor;
    private volatile boolean shutdown = false;

    private AsyncRuntime(Builder builder) {
        this.threadNamePrefix = builder.threadNamePrefix;
        this.taskDecorator = builder.taskDecorator;
        this.uncaughtExceptionHandler = builder.uncaughtExceptionHandler;
        this.launcher = new VirtualThreadLauncher(this.threadNamePrefix, this.uncaughtExceptionHandler);
        this.executorService = builder.executorService;
        this.ownsExecutor = builder.ownsExecutor;
    }

    /**
     * Creates a new runtime builder.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Submits an asynchronous operation for immediate execution.
     *
     * @param <T> the result type
     * @param operation the operation to execute
     * @return a Task representing the running operation
     * @throws IllegalStateException if the runtime is shut down
     */
    public <T> Task<T> async(Callable<? extends T> operation) {
        return async(null, operation);
    }

    /**
     * Submits a named asynchronous operation for immediate execution.
     *
     * @param <T> the result type
     * @param taskName logical name for the task
     * @param operation the operation to execute
     * @return a Task representing the running operation
     * @throws IllegalStateException if the runtime is shut down
     */
    public <T> Task<T> async(String taskName, Callable<? extends T> operation) {
        Task<T> task = createUnstartedTask(taskName, operation);
        task.start();
        return task;
    }

    /**
     * Creates a task in the CREATED (unstarted) state.
     *
     * @param <T> the result type
     * @param taskName logical name for the task
     * @param operation the operation to execute
     * @return an unstarted Task representing the operation
     * @throws IllegalStateException if the runtime is shut down
     */
    public <T> Task<T> createUnstartedTask(String taskName, Callable<? extends T> operation) {
        ensureNotShutdown();
        Objects.requireNonNull(operation, "operation");

        final class OperationRunner implements Runnable {
            T value;
            Throwable exception;

            @Override
            public void run() {
                try {
                    value = operation.call();
                } catch (Throwable t) {
                    exception = t;
                }
            }
        }

        OperationRunner runner = new OperationRunner();
        Runnable decoratedRunnable = taskDecorator.decorate(runner);
        Objects.requireNonNull(decoratedRunnable, "TaskDecorator returned a null Runnable");

        Callable<T> callable = () -> {
            Throwable setupOrCleanupException = null;
            try {
                decoratedRunnable.run();
            } catch (Throwable t) {
                setupOrCleanupException = t;
            }

            Throwable userException = runner.exception;

            if (userException != null && setupOrCleanupException != null) {
                userException.addSuppressed(setupOrCleanupException);
                throwException(userException);
            } else if (userException != null) {
                throwException(userException);
            } else if (setupOrCleanupException != null) {
                throwException(setupOrCleanupException);
            }

            return runner.value;
        };

        Task<T> task;
        if (executorService != null) {
            java.util.function.Function<Task<T>, Runnable> startActionFactory = (t) -> () -> {
                executorService.execute(() -> {
                    t.setExecutingThread(Thread.currentThread());
                    t.futureTask().run();
                });
            };
            task = new Task<>(taskName, callable, null, startActionFactory);
        } else {
            java.util.function.Function<Task<T>, Runnable> startActionFactory = (t) -> {
                Thread thread = launcher.createUnstarted(taskName, t.futureTask());
                t.setExecutingThread(thread);
                return () -> thread.start();
            };
            task = new Task<>(taskName, callable, null, startActionFactory);
        }
        return task;
    }

    private static void throwException(Throwable t) throws Exception {
        if (t instanceof Exception ex) {
            throw ex;
        }
        if (t instanceof Error err) {
            throw err;
        }
        throw new RuntimeException(t);
    }

    /**
     * Submits a runnable operation for immediate execution.
     *
     * @param operation the operation to execute
     * @return a Task representing the running operation
     * @throws IllegalStateException if the runtime is shut down
     */
    public Task<Void> async(Runnable operation) {
        return async(null, operation);
    }

    /**
     * Submits a named runnable operation for immediate execution.
     *
     * @param taskName logical name for the task
     * @param operation the operation to execute
     * @return a Task representing the running operation
     * @throws IllegalStateException if the runtime is shut down
     */
    public Task<Void> async(String taskName, Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        return async(taskName, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * Awaits completion of the given task.
     *
     * @param <T> the result type
     * @param task the task to await
     * @return the result of the task
     */
    public <T> T await(Task<T> task) {
        Objects.requireNonNull(task, "task");
        return task.await();
    }

    /**
     * Awaits completion of the given task up to the specified timeout.
     *
     * @param <T> the result type
     * @param task the task to await
     * @param timeout maximum duration to wait
     * @return the result of the task
     */
    public <T> T await(Task<T> task, Duration timeout) {
        Objects.requireNonNull(task, "task");
        return task.await(timeout);
    }

    /**
     * Awaits completion of the given task up to the specified timeout, and cancels the task if it times out.
     *
     * @param <T> the result type
     * @param task the task to await
     * @param timeout maximum duration to wait
     * @return the result of the task
     */
    public <T> T awaitAndCancel(Task<T> task, Duration timeout) {
        Objects.requireNonNull(task, "task");
        try {
            return await(task, timeout);
        } catch (TaskTimeoutException e) {
            task.cancel(true);
            throw e;
        }
    }

    /**
     * Creates a new structured {@link TaskScope} bound to this runtime.
     *
     * @return a new TaskScope
     */
    public TaskScope scope() {
        return new TaskScope(this);
    }

    /**
     * Returns whether the runtime is shut down.
     *
     * @return {@code true} if shut down
     */
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public void close() {
        shutdown = true;
        if (ownsExecutor && executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void ensureNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("AsyncRuntime is shut down");
        }
    }

    /**
     * Awaits all tasks. If any task fails or is cancelled, it immediately fails-fast
     * and cancels all other tasks in the collection.
     *
     * @param <T> the task result type
     * @param tasks the tasks collection
     * @return the list of results in the same order as the inputs
     */
    public <T> List<T> all(Collection<Task<? extends T>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        CountDownLatch latch = new CountDownLatch(tasks.size());
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();

        for (Task<? extends T> task : tasks) {
            task.onComplete(() -> {
                Task.State state = task.lifecycleState();
                if (state == Task.State.FAILED) {
                    try {
                        task.await();
                    } catch (Throwable t) {
                        if (failure.compareAndSet(null, t)) {
                            for (Task<? extends T> other : tasks) {
                                if (other != task) {
                                    other.cancel(true);
                                }
                            }
                            long count = latch.getCount();
                            for (long i = 0; i < count; i++) {
                                latch.countDown();
                            }
                        }
                    }
                } else if (state == Task.State.CANCELLED) {
                    java.util.concurrent.CancellationException ce = new java.util.concurrent.CancellationException("Task " + task.name() + " was cancelled");
                    if (failure.compareAndSet(null, ce)) {
                        for (Task<? extends T> other : tasks) {
                            if (other != task) {
                                other.cancel(true);
                            }
                        }
                        long count = latch.getCount();
                        for (long i = 0; i < count; i++) {
                            latch.countDown();
                        }
                    }
                } else {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskInterruptedException("Awaiting thread was interrupted", e);
        }

        Throwable t = failure.get();
        if (t != null) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (t instanceof Error error) {
                throw error;
            }
            throw new TaskExecutionException(t);
        }

        List<T> results = new java.util.ArrayList<>(tasks.size());
        for (Task<? extends T> task : tasks) {
            results.add(task.await());
        }
        return results;
    }

    /**
     * Returns the result of the first task that succeeds. If all tasks fail,
     * throws an {@link AggregateException} containing all failures.
     *
     * @param <T> the task result type
     * @param tasks the tasks collection
     * @return the first successful result
     */
    public <T> T any(Collection<Task<? extends T>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks collection cannot be null or empty");
        }

        CountDownLatch latch = new CountDownLatch(tasks.size());
        java.util.concurrent.atomic.AtomicReference<T> successValue = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean hasSuccess = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (Task<? extends T> task : tasks) {
            task.onComplete(() -> {
                Task.State state = task.lifecycleState();
                if (state == Task.State.SUCCESS) {
                    try {
                        T value = task.await();
                        if (hasSuccess.compareAndSet(false, true)) {
                            successValue.set(value);
                            for (Task<? extends T> other : tasks) {
                                if (other != task) {
                                    other.cancel(true);
                                }
                            }
                            long count = latch.getCount();
                            for (long i = 0; i < count; i++) {
                                latch.countDown();
                            }
                        }
                    } catch (Throwable t) {
                        failures.add(t);
                        latch.countDown();
                    }
                } else {
                    try {
                        task.await();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskInterruptedException("Awaiting thread was interrupted", e);
        }

        if (hasSuccess.get()) {
            return successValue.get();
        }

        throw new AggregateException("All tasks failed", failures);
    }

    /**
     * Returns the result/exception of the first task that completes (succeeds,
     * fails, or cancels), and cancels all other tasks.
     *
     * @param <T> the task result type
     * @param tasks the tasks collection
     * @return the result of the first completed task
     */
    public <T> T race(Collection<Task<? extends T>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks collection cannot be null or empty");
        }

        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Task<? extends T>> firstCompleted = new java.util.concurrent.atomic.AtomicReference<>();

        for (Task<? extends T> task : tasks) {
            task.onComplete(() -> {
                if (firstCompleted.compareAndSet(null, task)) {
                    for (Task<? extends T> other : tasks) {
                        if (other != task) {
                            other.cancel(true);
                        }
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskInterruptedException("Awaiting thread was interrupted", e);
        }

        Task<? extends T> completed = firstCompleted.get();
        return completed.await();
    }

    /**
     * Awaits all tasks to settle (succeed, fail, or cancel) without throwing exceptions,
     * returning the inputs.
     *
     * @param <T> the task result type
     * @param tasks the tasks collection
     * @return the input collection of tasks after all have completed
     */
    public <T> Collection<Task<? extends T>> allSettled(Collection<Task<? extends T>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        CountDownLatch latch = new CountDownLatch(tasks.size());

        for (Task<? extends T> task : tasks) {
            task.onComplete(latch::countDown);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskInterruptedException("Awaiting thread was interrupted", e);
        }

        return tasks;
    }



    /**
     * Builder for configuring and creating an {@link AsyncRuntime}.
     */
    public static final class Builder {
        private String threadNamePrefix = "vt-task-";
        private TaskDecorator taskDecorator = TaskDecorator.identity();
        private Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> {};
        private ExecutorService executorService;
        private boolean ownsExecutor = false;

        private Builder() {
        }

        /**
         * Sets the thread name prefix for virtual threads started by this runtime.
         *
         * @param prefix the prefix
         * @return this builder
         */
        public Builder threadNamePrefix(String prefix) {
            this.threadNamePrefix = Objects.requireNonNull(prefix, "threadNamePrefix");
            return this;
        }

        /**
         * Sets the task decorator for thread context propagation.
         *
         * @param decorator the decorator
         * @return this builder
         */
        public Builder taskDecorator(TaskDecorator decorator) {
            this.taskDecorator = Objects.requireNonNull(decorator, "decorator");
            return this;
        }

        /**
         * Sets the uncaught exception handler for virtual threads.
         *
         * @param handler the handler
         * @return this builder
         */
        public Builder uncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler) {
            this.uncaughtExceptionHandler = Objects.requireNonNull(handler, "uncaughtExceptionHandler");
            return this;
        }

        /**
         * Configures a custom ExecutorService for running tasks.
         *
         * @param executorService the custom executor service
         * @param ownsExecutor whether this runtime owns the executor service and should close it when shut down
         * @return this builder
         */
        public Builder executorService(ExecutorService executorService, boolean ownsExecutor) {
            this.executorService = executorService;
            this.ownsExecutor = ownsExecutor;
            return this;
        }

        /**
         * Builds an immutable {@link AsyncRuntime}.
         *
         * @return a new AsyncRuntime instance
         */
        public AsyncRuntime build() {
            return new AsyncRuntime(this);
        }
    }
}
