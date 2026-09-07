package vt.async.await.spring;

import vt.async.await.AsyncTaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * AsyncTask decorator for Spring Security SecurityContext propagation.
 */
public final class SecurityAsyncTaskDecorator implements AsyncTaskDecorator {

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
