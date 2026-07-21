package io.lemonade.vtasyncawait;

/**
 * Functional interface representing an operation executed within a {@link TaskScope}.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface ScopedOperation<T> {

    /**
     * Executes the operation within the given scope.
     *
     * @param scope the active task scope
     * @return the result of the operation
     * @throws Exception if unable to compute a result
     */
    T run(TaskScope scope) throws Exception;
}
