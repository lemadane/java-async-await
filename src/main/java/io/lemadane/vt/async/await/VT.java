package io.lemadane.vt.async.await;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Static facade providing a convenient async/await API backed by virtual threads.
 *
 * <p>Example usage:
 * <pre>{@code
 * Task<Customer> customerTask = VT.async(() -> customerService.findRequired(id));
 * Customer customer = VT.await(customerTask);
 * }</pre>
 *
 * <p>Static import usage:
 * <pre>{@code
 * import static io.lemadane.vt.async.await.VT.async;
 * import static io.lemadane.vt.async.await.VT.await;
 *
 * Task<Customer> customerTask = async(() -> customerService.findRequired(id));
 * Customer customer = await(customerTask);
 * }</pre>
 */
public final class VT {

    private static final AsyncRuntime DEFAULT_RUNTIME = AsyncRuntime.builder().build();

    private VT() {
    }

    /**
     * Submits a callable operation for immediate execution on a virtual thread.
     *
     * @param <T> the result type
     * @param operation the operation to execute
     * @return a Task representing the running operation
     */
    public static <T> Task<T> async(Callable<? extends T> operation) {
        return DEFAULT_RUNTIME.async(operation);
    }

    /**
     * Submits a named callable operation for immediate execution on a virtual thread.
     *
     * @param <T> the result type
     * @param taskName logical name for the task
     * @param operation the operation to execute
     * @return a Task representing the running operation
     */
    public static <T> Task<T> async(String taskName, Callable<? extends T> operation) {
        return DEFAULT_RUNTIME.async(taskName, operation);
    }

    /**
     * Submits a runnable operation for immediate execution on a virtual thread.
     *
     * @param operation the operation to execute
     * @return a Task representing the running operation
     */
    public static Task<Void> async(Runnable operation) {
        return DEFAULT_RUNTIME.async(operation);
    }

    /**
     * Submits a named runnable operation for immediate execution on a virtual thread.
     *
     * @param taskName logical name for the task
     * @param operation the operation to execute
     * @return a Task representing the running operation
     */
    public static Task<Void> async(String taskName, Runnable operation) {
        return DEFAULT_RUNTIME.async(taskName, operation);
    }

    /**
     * Awaits completion of the given task.
     *
     * @param <T> the result type
     * @param task the task to await
     * @return the result of the task
     */
    public static <T> T await(Task<T> task) {
        return DEFAULT_RUNTIME.await(task);
    }

    /**
     * Awaits completion of the given task up to the specified timeout.
     *
     * @param <T> the result type
     * @param task the task to await
     * @param timeout maximum duration to wait
     * @return the result of the task
     */
    public static <T> T await(Task<T> task, Duration timeout) {
        return DEFAULT_RUNTIME.await(task, timeout);
    }

    /**
     * Awaits completion of the given task up to the specified timeout, and cancels the task if it times out.
     *
     * @param <T> the result type
     * @param task the task to await
     * @param timeout maximum duration to wait
     * @return the result of the task
     */
    public static <T> T awaitAndCancel(Task<T> task, Duration timeout) {
        return DEFAULT_RUNTIME.awaitAndCancel(task, timeout);
    }

    /**
     * Creates a new structured {@link TaskScope} using the default runtime.
     *
     * @return a new TaskScope
     */
    public static TaskScope scope() {
        return DEFAULT_RUNTIME.scope();
    }

    /**
     * Executes a scoped operation within an auto-closing {@link TaskScope}.
     *
     * @param <T> the result type
     * @param operation the operation to execute
     * @return the result of the operation
     */
    public static <T> T scoped(ScopedOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        try (TaskScope scope = scope()) {
            return operation.run(scope);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new TaskExecutionException(e);
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
    public static <T> java.util.List<T> all(java.util.Collection<Task<? extends T>> tasks) {
        return DEFAULT_RUNTIME.all(tasks);
    }

    /**
     * Returns the result of the first task that succeeds. If all tasks fail,
     * throws an {@link AggregateException} containing all failures.
     *
     * @param <T> the task result type
     * @param tasks the tasks collection
     * @return the first successful result
     */
    public static <T> T any(java.util.Collection<Task<? extends T>> tasks) {
        return DEFAULT_RUNTIME.any(tasks);
    }

    /**
     * Returns the result/exception of the first task that completes (succeeds,
     * fails, or cancels), and cancels all other tasks.
     *
     * @param <T> the task result type
     * @param tasks the tasks collection
     * @return the result of the first completed task
     */
    public static <T> T race(java.util.Collection<Task<? extends T>> tasks) {
        return DEFAULT_RUNTIME.race(tasks);
    }

    /**
     * Awaits all tasks to settle (succeed, fail, or cancel) without throwing exceptions,
     * returning the inputs.
     *
     * @param <T> the task result type
     * @param tasks the tasks collection
     * @return the input collection of tasks after all have completed
     */
    public static <T> java.util.Collection<Task<? extends T>> allSettled(java.util.Collection<Task<? extends T>> tasks) {
        return DEFAULT_RUNTIME.allSettled(tasks);
    }
}
