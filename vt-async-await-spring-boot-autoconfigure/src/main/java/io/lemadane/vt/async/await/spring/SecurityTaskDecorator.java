package io.lemadane.vt.async.await.spring;

import io.lemadane.vt.async.await.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Task decorator for Spring Security SecurityContext propagation.
 */
public final class SecurityTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable operation) {
        SecurityContext context = SecurityContextHolder.getContext();
        return () -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(context);
            try {
                operation.run();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        };
    }
}
