package io.lemadane.vt.async.await.spring;

import io.lemadane.vt.async.await.TaskDecorator;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Task decorator for Spring LocaleContext propagation.
 */
public final class LocaleTaskDecorator implements TaskDecorator {

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
