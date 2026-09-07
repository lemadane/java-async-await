package vt.async.await.spring;

import vt.async.await.AsyncTaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * AsyncTask decorator for Spring RequestAttributes context propagation.
 */
public final class RequestContextAsyncTaskDecorator implements AsyncTaskDecorator {

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
