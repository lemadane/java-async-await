package vt.async.await.spring;

import vt.async.await.AsyncTaskDecorator;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * AsyncTask decorator for Spring LocaleContext propagation.
 */
public final class LocaleAsyncTaskDecorator implements AsyncTaskDecorator {

    @Override
    public Runnable decorate(Runnable operation) {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        return () -> {
            LocaleContext previous = LocaleContextHolder.getLocaleContext();
            LocaleContextHolder.setLocaleContext(context);
            try {
                operation.run();
            } finally {
                LocaleContextHolder.setLocaleContext(previous);
            }
        };
    }
}
