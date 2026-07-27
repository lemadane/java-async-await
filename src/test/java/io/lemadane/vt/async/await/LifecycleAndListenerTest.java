package io.lemadane.vt.async.await;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleAndListenerTest {

    static class TestDecorator implements TaskDecorator {
        final AtomicBoolean setupFailed = new AtomicBoolean(false);
        final AtomicBoolean cleanupFailed = new AtomicBoolean(false);
        final AtomicBoolean runCalled = new AtomicBoolean(false);

        @Override
        public Runnable decorate(Runnable operation) {
            return () -> {
                if (setupFailed.get()) {
                    throw new RuntimeException("setup failure");
                }
                try {
                    runCalled.set(true);
                    operation.run();
                } finally {
                    if (cleanupFailed.get()) {
                        throw new RuntimeException("cleanup failure");
                    }
                }
            };
        }
    }

    // Lifecycle Consistency Tests

    @Test
    void testOperationAndCleanupSucceed() {
        TestDecorator decorator = new TestDecorator();
        AsyncRuntime runtime = AsyncRuntime.builder().taskDecorator(decorator).build();

        Task<String> task = runtime.async(() -> "success");
        assertEquals("success", runtime.await(task));
        assertEquals(Task.State.SUCCESS, task.lifecycleState());
        assertTrue(decorator.runCalled.get());

        runtime.close();
    }

    @Test
    void testOperationFails() {
        TestDecorator decorator = new TestDecorator();
        AsyncRuntime runtime = AsyncRuntime.builder().taskDecorator(decorator).build();

        Task<String> task = runtime.async(() -> {
            throw new RuntimeException("operation failure");
        });

        RuntimeException ex = assertThrows(RuntimeException.class, () -> runtime.await(task));
        assertEquals("operation failure", ex.getMessage());
        assertEquals(Task.State.FAILED, task.lifecycleState());
        assertTrue(decorator.runCalled.get());

        runtime.close();
    }

    @Test
    void testDecoratorSetupFails() {
        TestDecorator decorator = new TestDecorator();
        decorator.setupFailed.set(true);
        AsyncRuntime runtime = AsyncRuntime.builder().taskDecorator(decorator).build();

        Task<String> task = runtime.async(() -> "never run");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> runtime.await(task));
        assertEquals("setup failure", ex.getMessage());
        assertEquals(Task.State.FAILED, task.lifecycleState());
        assertFalse(decorator.runCalled.get());

        runtime.close();
    }

    @Test
    void testOperationSucceedsCleanupFails() {
        TestDecorator decorator = new TestDecorator();
        decorator.cleanupFailed.set(true);
        AsyncRuntime runtime = AsyncRuntime.builder().taskDecorator(decorator).build();

        Task<String> task = runtime.async(() -> "user success");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> runtime.await(task));
        assertEquals("cleanup failure", ex.getMessage());
        assertEquals(Task.State.FAILED, task.lifecycleState());
        assertTrue(decorator.runCalled.get());

        runtime.close();
    }

    @Test
    void testOperationFailsCleanupFails() {
        TestDecorator decorator = new TestDecorator();
        decorator.cleanupFailed.set(true);
        AsyncRuntime runtime = AsyncRuntime.builder().taskDecorator(decorator).build();

        Task<String> task = runtime.async(() -> {
            throw new IOException("user failure");
        });

        TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> runtime.await(task));
        assertEquals("user failure", ex.getCause().getMessage());
        assertEquals(Task.State.FAILED, task.lifecycleState());

        Throwable[] suppressed = ex.getCause().getSuppressed();
        assertEquals(1, suppressed.length);
        assertEquals("cleanup failure", suppressed[0].getMessage());

        runtime.close();
    }

    @Test
    void testCancelBeforeStart() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.createUnstartedTask("unstarted", () -> "ok");

        assertTrue(task.cancel(true));
        assertEquals(Task.State.CANCELLED, task.lifecycleState());

        task.start();
        assertThrows(CancellationException.class, task::await);

        runtime.close();
    }

    @Test
    void testCancelRunningTask() throws Exception {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Task<String> task = runtime.async(() -> {
            started.countDown();
            release.await();
            return "ok";
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(task.cancel(true));
        assertEquals(Task.State.CANCELLED, task.lifecycleState());
        assertTrue(task.isCancelled());

        release.countDown();
        runtime.close();
    }

    @Test
    void testCancelAfterSuccess() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.async(() -> "success");
        assertEquals("success", runtime.await(task));

        assertFalse(task.cancel(true));
        assertEquals(Task.State.SUCCESS, task.lifecycleState());

        runtime.close();
    }

    @Test
    void testCancelAfterFailure() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.async(() -> {
            throw new RuntimeException("fail");
        });
        assertThrows(RuntimeException.class, () -> runtime.await(task));

        assertFalse(task.cancel(true));
        assertEquals(Task.State.FAILED, task.lifecycleState());

        runtime.close();
    }

    @Test
    void testNoRunningTaskRemainsRunningAfterCompletion() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.async(() -> "done");
        assertEquals("done", runtime.await(task));
        assertFalse(task.isRunning());
        runtime.close();
    }

    // Exactly-Once Listener Tests

    @Test
    void testListenerExecutesOnceAfterSuccess() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.async(() -> "success");

        AtomicInteger count = new AtomicInteger(0);
        task.onComplete(count::incrementAndGet);

        assertEquals("success", runtime.await(task));
        assertEquals(1, count.get());
        runtime.close();
    }

    @Test
    void testListenerExecutesOnceAfterFailure() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.async(() -> {
            throw new RuntimeException("fail");
        });

        AtomicInteger count = new AtomicInteger(0);
        task.onComplete(count::incrementAndGet);

        assertThrows(RuntimeException.class, () -> runtime.await(task));
        assertEquals(1, count.get());
        runtime.close();
    }

    @Test
    void testListenerExecutesOnceAfterCancellation() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.createUnstartedTask("unstarted", () -> "ok");

        AtomicInteger count = new AtomicInteger(0);
        task.onComplete(count::incrementAndGet);

        assertTrue(task.cancel(true));
        assertEquals(1, count.get());
        runtime.close();
    }

    @Test
    void testListenerFailingDoesNotBlockOthers() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.async(() -> "ok");

        AtomicInteger count = new AtomicInteger(0);
        task.onComplete(() -> {
            throw new RuntimeException("listener failure");
        });
        task.onComplete(count::incrementAndGet);

        assertEquals("ok", runtime.await(task));
        assertEquals(1, count.get());
        runtime.close();
    }

    @Test
    void testListenerAddedAfterCompletionExecutesImmediately() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.async(() -> "ok");
        assertEquals("ok", runtime.await(task));

        AtomicInteger count = new AtomicInteger(0);
        task.onComplete(count::incrementAndGet);
        assertEquals(1, count.get());
        runtime.close();
    }

    // Combinator Regression Tests

    @Test
    void testAllWithDecoratorSetupFailure() {
        TestDecorator decorator1 = new TestDecorator();
        TestDecorator decorator2 = new TestDecorator();
        decorator2.setupFailed.set(true);

        AsyncRuntime r1 = AsyncRuntime.builder().taskDecorator(decorator1).build();
        AsyncRuntime r2 = AsyncRuntime.builder().taskDecorator(decorator2).build();

        Task<String> t1 = r1.async(() -> "one");
        Task<String> t2 = r2.async(() -> "two");

        assertThrows(RuntimeException.class, () -> VT.all(Arrays.asList(t1, t2)));
        assertTrue(t1.isCancelled() || t1.isDone());
        assertEquals(Task.State.FAILED, t2.lifecycleState());

        r1.close();
        r2.close();
    }

    @Test
    void testAllWithCleanupFailure() {
        TestDecorator decorator1 = new TestDecorator();
        TestDecorator decorator2 = new TestDecorator();
        decorator2.cleanupFailed.set(true);

        AsyncRuntime r1 = AsyncRuntime.builder().taskDecorator(decorator1).build();
        AsyncRuntime r2 = AsyncRuntime.builder().taskDecorator(decorator2).build();

        Task<String> t1 = r1.async(() -> "one");
        Task<String> t2 = r2.async(() -> "two");

        assertThrows(RuntimeException.class, () -> VT.all(Arrays.asList(t1, t2)));
        assertTrue(t1.isCancelled() || t1.isDone());
        assertEquals(Task.State.FAILED, t2.lifecycleState());

        r1.close();
        r2.close();
    }

    @Test
    void testAnyEarlyFailureLaterSuccess() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> t1 = runtime.async(() -> {
            throw new RuntimeException("fail fast");
        });
        Task<String> t2 = runtime.async(() -> {
            Thread.sleep(50);
            return "success";
        });

        String val = runtime.any(Arrays.asList(t1, t2));
        assertEquals("success", val);
        runtime.close();
    }

    @Test
    void testRepeatedAwaitOwnershipValidity() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        try (TaskScope scope = runtime.scope()) {
            Task<String> task = scope.async(() -> "val");

            // First await
            assertEquals("val", scope.await(task));

            // Second await should also succeed and not throw IllegalArgumentException!
            assertEquals("val", scope.await(task));
        }
        runtime.close();
    }
}
