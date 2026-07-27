package io.lemadane.vt.async.await;

import io.lemadane.vt.async.await.internal.ExceptionSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ComprehensiveTest {

    @Test
    void testSuccessfulResult() {
        Task<String> task = VT.async(() -> "success");
        assertEquals("success", VT.await(task));
    }

    @Test
    void testNullResult() {
        Task<Void> task = VT.async(() -> null);
        assertNull(VT.await(task));
    }

    @Test
    void testRuntimeException() {
        Task<String> task = VT.async(() -> {
            throw new IllegalArgumentException("runtime exception");
        });
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> VT.await(task));
        assertEquals("runtime exception", ex.getMessage());
    }

    @Test
    void testCheckedException() {
        Task<String> task = VT.async(() -> {
            throw new IOException("checked exception");
        });
        TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> VT.await(task));
        assertInstanceOf(IOException.class, ex.getCause());
        assertEquals("checked exception", ex.getCause().getMessage());
    }

    @Test
    void testErrorRethrown() {
        Task<String> task = VT.async(() -> {
            throw new StackOverflowError("error");
        });
        StackOverflowError err = assertThrows(StackOverflowError.class, () -> VT.await(task));
        assertEquals("error", err.getMessage());
    }

    @Test
    void testCancellationBeforeStart() throws Exception {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        Task<String> task = runtime.createUnstartedTask("unstarted", () -> "ok");
        assertTrue(task.cancel(true));
        assertTrue(task.isCancelled());
        assertTrue(task.isDone());
        assertEquals(Task.State.CANCELLED, task.lifecycleState());

        task.start(); // Should do nothing
        assertThrows(CancellationException.class, task::await);
        runtime.close();
    }

    @Test
    void testCancellationWhileRunning() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<String> task = VT.async(() -> {
            running.countDown();
            release.await();
            return "never";
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));
        assertTrue(task.cancel(true));
        assertTrue(task.isCancelled());
        assertTrue(task.isDone());
        assertEquals(Task.State.CANCELLED, task.lifecycleState());
        assertThrows(CancellationException.class, task::await);
        release.countDown();
    }

    @Test
    void testCancellationAfterCompletion() {
        Task<String> task = VT.async(() -> "completed");
        assertEquals("completed", VT.await(task));
        assertFalse(task.cancel(true));
        assertFalse(task.isCancelled());
        assertTrue(task.isDone());
    }

    @Test
    void testMultipleCancellationAttempts() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<String> task = VT.async(() -> {
            running.countDown();
            release.await();
            return "never";
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));
        assertTrue(task.cancel(true));
        assertFalse(task.cancel(true)); // Second attempt returns false
        release.countDown();
    }

    @Test
    void testMultipleAwaits() {
        Task<String> task = VT.async(() -> "val");
        assertEquals("val", VT.await(task));
        assertEquals("val", VT.await(task));
    }

    @Test
    void testConcurrentAwaits() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch startAwait = new CountDownLatch(1);
        Task<String> task = VT.async(() -> {
            running.countDown();
            Thread.sleep(100);
            return "val";
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));

        AtomicReference<String> result1 = new AtomicReference<>();
        AtomicReference<String> result2 = new AtomicReference<>();

        Thread t1 = Thread.ofVirtual().start(() -> {
            startAwait.countDown();
            result1.set(VT.await(task));
        });

        Thread t2 = Thread.ofVirtual().start(() -> {
            result2.set(VT.await(task));
        });

        t1.join();
        t2.join();

        assertEquals("val", result1.get());
        assertEquals("val", result2.get());
    }

    @Test
    void testAwaitAfterCancellation() {
        Task<String> task = VT.async(() -> {
            Thread.sleep(5000);
            return "never";
        });
        task.cancel(true);
        assertThrows(CancellationException.class, task::await);
    }

    @Test
    void testAwaitAfterFailure() {
        Task<String> task = VT.async(() -> {
            throw new RuntimeException("failed");
        });
        RuntimeException ex = assertThrows(RuntimeException.class, () -> VT.await(task));
        assertEquals("failed", ex.getMessage());
        RuntimeException ex2 = assertThrows(RuntimeException.class, () -> VT.await(task));
        assertEquals("failed", ex2.getMessage());
    }

    @Test
    void testAwaitAfterSuccess() {
        Task<String> task = VT.async(() -> "success");
        assertEquals("success", VT.await(task));
        assertEquals("success", VT.await(task));
    }

    @Test
    void testRuntimeShutdownAndSubmissionAfterShutdown() {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        runtime.close();
        assertTrue(runtime.isShutdown());

        assertThrows(IllegalStateException.class, () -> runtime.async(() -> "fail"));
    }

    @Test
    void testCloseEmptyScope() {
        try (TaskScope scope = VT.scope()) {
            assertFalse(scope.isClosed());
        }
    }

    @Test
    void testCloseScopeWithCompletedTasks() {
        try (TaskScope scope = VT.scope()) {
            Task<String> task = scope.async(() -> "done");
            assertEquals("done", scope.await(task));
        }
    }

    @Test
    void testCloseScopeWithRunningTasks() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptedLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Task<Void> child;
        try (TaskScope scope = VT.scope()) {
            child = scope.async(() -> {
                started.countDown();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    interruptedLatch.countDown();
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
        }
        assertTrue(child.isCancelled());
        assertTrue(interruptedLatch.await(2, TimeUnit.SECONDS));
        assertTrue(interrupted.get());
    }

    @Test
    void testCloseScopeMultipleTimesIdempotent() {
        TaskScope scope = VT.scope();
        scope.close();
        assertTrue(scope.isClosed());
        scope.close(); // safe and idempotent
    }

    @Test
    void testSubmitAfterCloseFails() {
        TaskScope scope = VT.scope();
        scope.close();
        assertThrows(IllegalStateException.class, () -> scope.async(() -> "fail"));
    }

    @Test
    void testNestedScopesBehaveIndependently() {
        try (TaskScope parent = VT.scope()) {
            Task<String> parentTask = parent.async(() -> "parent");
            try (TaskScope child = VT.scope()) {
                Task<String> childTask = child.async(() -> "child");
                assertEquals("child", child.await(childTask));
            }
            assertEquals("parent", parent.await(parentTask));
        }
    }

    @Test
    void testScopeDeadlockFreeWhenClosedFromInsideChildTask() throws Exception {
        CountDownLatch closeDone = new CountDownLatch(1);
        TaskScope scope = VT.scope();
        try {
            scope.async(() -> {
                scope.close();
                closeDone.countDown();
            });
            assertTrue(closeDone.await(5, TimeUnit.SECONDS));
        } finally {
            scope.close();
        }
    }

    @Test
    void testAwaitTimeoutWithoutCancellation() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<String> task = VT.async("slow", () -> {
            running.countDown();
            release.await();
            return "done";
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));

        assertThrows(TaskTimeoutException.class, () -> VT.await(task, Duration.ofMillis(50)));
        assertTrue(task.isRunning()); // Task is still running!

        release.countDown();
        assertEquals("done", VT.await(task));
    }

    @Test
    void testAwaitTimeoutWithCancellation() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        Task<String> task = VT.async("slow", () -> {
            running.countDown();
            Thread.sleep(5000);
            return "done";
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));

        assertThrows(TaskTimeoutException.class, () -> VT.awaitAndCancel(task, Duration.ofMillis(50)));
        assertTrue(task.isCancelled()); // Task was cancelled!
    }

    @Test
    void testInvalidTimeoutDurations() {
        Task<String> task = VT.async(() -> "val");
        assertThrows(IllegalArgumentException.class, () -> VT.await(task, Duration.ofMillis(-5)));
    }

    @Test
    void testAwaitingThreadInterrupted() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<String> task = VT.async(() -> {
            running.countDown();
            release.await();
            return "done";
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));

        CountDownLatch interruptedThrown = new CountDownLatch(1);
        Thread awaiter = Thread.ofVirtual().start(() -> {
            try {
                VT.await(task);
            } catch (TaskInterruptedException e) {
                interruptedThrown.countDown();
            }
        });

        awaiter.interrupt();
        assertTrue(interruptedThrown.await(2, TimeUnit.SECONDS));
        release.countDown();
    }

    @Test
    void testChildTaskThrowsInterruptedExceptionDoesNotInterruptAwaitingThread() {
        Task<String> task = VT.async(() -> {
            throw new InterruptedException("child interrupted");
        });

        TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> VT.await(task));
        assertInstanceOf(InterruptedException.class, ex.getCause());
        assertFalse(Thread.currentThread().isInterrupted()); // Awaiting thread is NOT interrupted!
    }

    @Test
    void testDecoratorCompositionAndOrder() {
        AtomicReference<String> order = new AtomicReference<>("");
        TaskDecorator d1 = op -> () -> {
            order.accumulateAndGet("1", (o, n) -> o + n);
            op.run();
        };
        TaskDecorator d2 = op -> () -> {
            order.accumulateAndGet("2", (o, n) -> o + n);
            op.run();
        };

        AsyncRuntime runtime = AsyncRuntime.builder()
                .taskDecorator(d1.andThen(d2))
                .build();

        Task<Void> task = runtime.async(() -> {});
        runtime.await(task);
        assertEquals("12", order.get());
        runtime.close();
    }

    @Test
    void testDecoratorFailurePreservesException() {
        TaskDecorator failingDecorator = op -> {
            throw new RuntimeException("decorator setup failed");
        };

        AsyncRuntime runtime = AsyncRuntime.builder()
                .taskDecorator(failingDecorator)
                .build();

        // Throws immediately during submission
        assertThrows(RuntimeException.class, () -> runtime.async(() -> "val"));
        runtime.close();
    }

    @Test
    void testResourceOwnershipOwnedExecutorServiceClosed() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        AsyncRuntime runtime = AsyncRuntime.builder()
                .executorService(executor, true)
                .build();

        runtime.close();
        assertTrue(executor.isShutdown());
    }

    @Test
    void testResourceOwnershipCallerOwnedExecutorServiceNotClosed() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        AsyncRuntime runtime = AsyncRuntime.builder()
                .executorService(executor, false)
                .build();

        runtime.close();
        assertFalse(executor.isShutdown());
        executor.close();
    }
}
