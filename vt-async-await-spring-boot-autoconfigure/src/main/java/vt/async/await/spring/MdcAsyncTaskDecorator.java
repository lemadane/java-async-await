package vt.async.await.spring;

import vt.async.await.AsyncTaskDecorator;
import org.slf4j.MDC;
import java.util.Map;

/**
 * AsyncTask decorator for SLF4J MDC context propagation.
 */
public final class MdcAsyncTaskDecorator implements AsyncTaskDecorator {

    @Override
    public Runnable decorate(Runnable operation) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }
            try {
                operation.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
