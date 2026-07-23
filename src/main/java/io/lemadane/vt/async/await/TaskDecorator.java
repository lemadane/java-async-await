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
     * Returns an identity task decorator that performs no transformation.
     *
     * @return the identity decorator
     */
    static TaskDecorator identity() {
        return operation -> operation;
    }
}
