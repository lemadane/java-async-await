package io.lemadane.vt.async.await;

/**
 * Functional interface for decorating task operations.
 * <p>
 * Useful for capturing caller context (e.g. MDC, request context, tracing)
 * on the submitted thread and restoring/cleaning up context on the virtual thread.
 */
@FunctionalInterface
public interface TaskDecorator {

    /**
     * Decorates the given runnable operation.
     *
     * @param operation the original runnable operation
     * @return the decorated runnable operation
     */
    Runnable decorate(Runnable operation);

    /**
     * Composes this decorator with another.
     *
     * @param after the decorator to run after this one
     * @return the composed decorator
     */
    default TaskDecorator andThen(TaskDecorator after) {
        java.util.Objects.requireNonNull(after, "after");
        return operation -> this.decorate(after.decorate(operation));
    }

    /**
     * Returns an identity task decorator that performs no transformation.
     *
     * @return the identity decorator
     */
    static TaskDecorator identity() {
        return operation -> operation;
    }
}
