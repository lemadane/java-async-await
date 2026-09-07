package vt.async.await;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CombinatorTest {

    @Test
    void testAllSuccess() {
        AsyncTask<String> t1 = VT.async(() -> "one");
        AsyncTask<String> t2 = VT.async(() -> "two");
        AsyncTask<String> t3 = VT.async(() -> "three");

        List<String> results = VT.all(Arrays.asList(t1, t2, t3));
        assertEquals(Arrays.asList("one", "two", "three"), results);
    }

    @Test
    void testOrFailFastAndCancelOthers() throws Exception {
        CountDownLatch runLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        AsyncTask<String> t1 = VT.async(() -> {
            runLatch.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                cancelLatch.countDown();
            }
            return "slow";
        });

        AsyncTask<String> t2 = VT.async(() -> {
            runLatch.await();
            throw new IOException("immediate fail");
        });

        AsyncTaskExecutionException ex = assertThrows(AsyncTaskExecutionException.class, () -> {
            VT.all(Arrays.asList(t1, t2));
        });

        assertInstanceOf(IOException.class, ex.getCause());
        assertEquals("immediate fail", ex.getCause().getMessage());

        assertTrue(cancelLatch.await(2, TimeUnit.SECONDS), "Other task should be cancelled and interrupted");
        assertTrue(t1.isCancelled());
    }

    @Test
    void testAnySuccess() throws Exception {
        CountDownLatch runLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        AsyncTask<String> t1 = VT.async(() -> {
            runLatch.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                cancelLatch.countDown();
            }
            return "slow";
        });

        AsyncTask<String> t2 = VT.async(() -> {
            runLatch.await();
            return "fast success";
        });

        String result = VT.any(Arrays.asList(t1, t2));
        assertEquals("fast success", result);

        assertTrue(cancelLatch.await(2, TimeUnit.SECONDS), "Slow task should be cancelled");
        assertTrue(t1.isCancelled());
    }

    @Test
    void testAnyAllFail() {
        AsyncTask<String> t1 = VT.async(() -> {
            throw new IllegalArgumentException("fail 1");
        });
        AsyncTask<String> t2 = VT.async(() -> {
            throw new IOException("fail 2");
        });

        AggregateException ex = assertThrows(AggregateException.class, () -> {
            VT.any(Arrays.asList(t1, t2));
        });

        assertEquals("All tasks failed", ex.getMessage());
        assertEquals(2, ex.getCauses().size());
    }

    @Test
    void testRaceSuccess() throws Exception {
        CountDownLatch runLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        AsyncTask<String> t1 = VT.async(() -> {
            runLatch.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                cancelLatch.countDown();
            }
            return "slow";
        });

        AsyncTask<String> t2 = VT.async(() -> {
            runLatch.await();
            return "fast race win";
        });

        String result = VT.race(Arrays.asList(t1, t2));
        assertEquals("fast race win", result);

        assertTrue(cancelLatch.await(2, TimeUnit.SECONDS), "Slow task should be cancelled");
        assertTrue(t1.isCancelled());
    }

    @Test
    void testRaceFailure() throws Exception {
        CountDownLatch runLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        AsyncTask<String> t1 = VT.async(() -> {
            runLatch.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                cancelLatch.countDown();
            }
            return "slow";
        });

        AsyncTask<String> t2 = VT.async(() -> {
            runLatch.await();
            throw new RuntimeException("fast fail");
        });

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            VT.race(Arrays.asList(t1, t2));
        });

        assertEquals("fast fail", ex.getMessage());
        assertTrue(cancelLatch.await(2, TimeUnit.SECONDS), "Slow task should be cancelled");
        assertTrue(t1.isCancelled());
    }

    @Test
    void testAllSettled() {
        AsyncTask<String> t1 = VT.async(() -> "success");
        AsyncTask<String> t2 = VT.async(() -> {
            throw new RuntimeException("fail");
        });
        AsyncTask<String> t3 = VT.async(() -> {
            Thread.sleep(5000);
            return "never";
        });
        t3.cancel(true);

        Collection<AsyncTask<? extends String>> settled = VT.allSettled(Arrays.asList(t1, t2, t3));
        assertEquals(3, settled.size());
        assertTrue(t1.isDone() && !t1.isCancelled());
        assertTrue(t2.isDone() && t2.lifecycleState() == AsyncTask.State.FAILED);
        assertTrue(t3.isDone() && t3.isCancelled());
    }

    @Test
    void testScopeOwnershipValidation() {
        try (AsyncTaskScope scope1 = VT.scope()) {
            AsyncTask<String> task1 = scope1.async(() -> "scope 1");
            try (AsyncTaskScope scope2 = VT.scope()) {
                assertThrows(IllegalArgumentException.class, () -> {
                    scope2.all(Arrays.asList(task1));
                });
                assertThrows(IllegalArgumentException.class, () -> {
                    scope2.any(Arrays.asList(task1));
                });
                assertThrows(IllegalArgumentException.class, () -> {
                    scope2.race(Arrays.asList(task1));
                });
                assertThrows(IllegalArgumentException.class, () -> {
                    scope2.allSettled(Arrays.asList(task1));
                });
            }
        }
    }
}
