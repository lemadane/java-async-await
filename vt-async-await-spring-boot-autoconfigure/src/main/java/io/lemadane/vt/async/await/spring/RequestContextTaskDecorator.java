package io.lemadane.vt.async.await.spring;

import io.lemadane.vt.async.await.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Task decorator for Spring RequestAttributes context propagation.
 */
public final class RequestContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable operation) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return () -> {
            RequestAttributes previous = RequestContextHolder.getRequestAttributes();
            RequestContextHolder.setRequestAttributes(attributes);
            try {
                operation.run();
            } finally {
                RequestContextHolder.setRequestAttributes(previous);
            }
        };
    }
}
