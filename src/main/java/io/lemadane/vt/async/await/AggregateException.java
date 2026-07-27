package io.lemadane.vt.async.await;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when multiple tasks fail during a collective await operation (such as any).
 */
public final class AggregateException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient List<Throwable> causes;

    /**
     * Constructs a new AggregateException with a message and causes.
     *
     * @param message the detail message
     * @param causes the list of causing throwables
     */
    public AggregateException(String message, List<Throwable> causes) {
        super(message);
        this.causes = Collections.unmodifiableList(causes);
        if (!causes.isEmpty()) {
            initCause(causes.get(0));
        }
    }

    /**
     * Returns the list of all causing throwables.
     *
     * @return the list of throwables
     */
    public List<Throwable> getCauses() {
        return causes;
    }
}
