package io.lemonade.vtasyncawait;

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
 * import static io.lemonade.vtasyncawait.VT.async;
 * import static io.lemonade.vtasyncawait.VT.await;
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
}
