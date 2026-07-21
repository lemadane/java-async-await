package io.lemonade.vtasyncawait;

import io.lemonade.vtasyncawait.internal.VirtualThreadLauncher;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * Configurable, injectable runtime for launching and awaiting virtual thread tasks.
 */
public final class AsyncRuntime {

    private final String threadNamePrefix;
    private final TaskDecorator taskDecorator;
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private final VirtualThreadLauncher launcher;

    private AsyncRuntime(Builder builder) {
        this.threadNamePrefix = builder.threadNamePrefix;
        this.taskDecorator = builder.taskDecorator;
        this.uncaughtExceptionHandler = builder.uncaughtExceptionHandler;
        this.launcher = new VirtualThreadLauncher(this.threadNamePrefix, this.uncaughtExceptionHandler);
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
     * Submits an asynchronous operation for immediate execution on a virtual thread.
     *
     * @param <T> the result type
     * @param operation the operation to execute
     * @return a Task representing the running operation
     */
    public <T> Task<T> async(Callable<? extends T> operation) {
        return async(null, operation);
    }

    /**
     * Submits a named asynchronous operation for immediate execution on a virtual thread.
     *
     * @param <T> the result type
     * @param taskName logical name for the task
     * @param operation the operation to execute
     * @return a Task representing the running operation
     */
    public <T> Task<T> async(String taskName, Callable<? extends T> operation) {
        Objects.requireNonNull(operation, "operation");

        CallableHolder<T> holder = new CallableHolder<>();
        Runnable wrapper = () -> {
            try {
                holder.value = operation.call();
            } catch (Exception e) {
                holder.exception = e;
            }
        };

        // Capture context on the caller thread
        Runnable decoratedRunnable = taskDecorator.decorate(wrapper);

        Callable<T> callable = () -> {
            decoratedRunnable.run();
            if (holder.exception != null) {
                throw holder.exception;
            }
            return holder.value;
        };

        FutureTask<T> futureTask = new FutureTask<>(callable);
        Thread thread = launcher.launch(taskName, futureTask);
        return new Task<>(taskName, futureTask, thread);
    }

    /**
     * Submits a runnable operation for immediate execution on a virtual thread.
     *
     * @param operation the operation to execute
     * @return a Task representing the running operation
     */
    public Task<Void> async(Runnable operation) {
        return async(null, operation);
    }

    /**
     * Submits a named runnable operation for immediate execution on a virtual thread.
     *
     * @param taskName logical name for the task
     * @param operation the operation to execute
     * @return a Task representing the running operation
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
     * Creates a new structured {@link TaskScope} bound to this runtime.
     *
     * @return a new TaskScope
     */
    public TaskScope scope() {
        return new TaskScope(this);
    }

    private static final class CallableHolder<T> {
        T value;
        Exception exception;
    }

    /**
     * Builder for configuring and creating an {@link AsyncRuntime}.
     */
    public static final class Builder {
        private String threadNamePrefix = "vt-task-";
        private TaskDecorator taskDecorator = TaskDecorator.identity();
        private Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> {};

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
         * Builds an immutable {@link AsyncRuntime}.
         *
         * @return a new AsyncRuntime instance
         */
        public AsyncRuntime build() {
            return new AsyncRuntime(this);
        }
    }
}
