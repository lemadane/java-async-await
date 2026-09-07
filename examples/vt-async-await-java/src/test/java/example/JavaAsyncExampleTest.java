package example;

import org.junit.jupiter.api.Test;
import vt.async.await.AsyncTask;
import vt.async.await.AsyncTaskTimeoutException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaAsyncExampleTest {

    @Test
    void verifiesBasicJavaAsyncExample() {
        assertEquals("Profile(C123) + Orders(C123)", JavaAsyncExample.loadCustomerData("C123"));
        assertEquals("Customer(C123) + Points(100)", JavaAsyncExample.loadWithCustomRuntime("C123"));
    }

    @Test
    void verifiesScopedLambda() {
        assertEquals("Profile(C123)", JavaAsyncExample.loadScopedLambda("C123"));
    }

    @Test
    void verifiesOnCompleteCallback() {
        AtomicBoolean called = new AtomicBoolean(false);
        String result = JavaAsyncExample.loadWithOnCompleteCallback("C123", () -> called.set(true));
        assertEquals("Profile(C123)", result);
        assertTrue(called.get());
    }

    @Test
    void verifiesUnstartedTask() {
        String result = JavaAsyncExample.loadUnstartedTask("C123");
        assertEquals("LazyProfile(C123)", result);
    }

    @Test
    void verifiesAllCombinator() {
        List<String> results = JavaAsyncExample.loadAllServices("C123");
        assertEquals(Arrays.asList("Profile(C123)", "Orders(C123)", "Preferences(C123)"), results);
    }

    @Test
    void verifiesRaceCombinator() {
        String fastest = JavaAsyncExample.loadFastestProvider();
        assertEquals("Fast Provider", fastest);
    }

    @Test
    void verifiesAnyCombinator() {
        String anyAvailable = JavaAsyncExample.loadAnyAvailableProvider();
        assertEquals("Backup Provider", anyAvailable);
    }

    @Test
    void verifiesAllSettledCombinator() {
        Collection<AsyncTask<? extends String>> settled = JavaAsyncExample.loadSettledSummary();
        assertEquals(3, settled.size());
    }

    @Test
    void verifiesAwaitAndCancel() {
        String result = JavaAsyncExample.loadWithStrictTimeout("C123", Duration.ofSeconds(2));
        assertEquals("Profile(C123)", result);
    }

    @Test
    void verifiesAwaitAndCancelTimeout() {
        AsyncTask<String> slowTask = vt.async.await.VT.async(() -> {
            Thread.sleep(5000);
            return "too slow";
        });
        assertThrows(AsyncTaskTimeoutException.class, () ->
                vt.async.await.VT.awaitAndCancel(slowTask, Duration.ofMillis(50))
        );
        assertTrue(slowTask.isCancelled());
    }
}
