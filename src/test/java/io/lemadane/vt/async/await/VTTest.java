package io.lemadane.vt.async.await;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.lemadane.vt.async.await.VT.async;
import static io.lemadane.vt.async.await.VT.await;
import static org.junit.jupiter.api.Assertions.*;

class VTTest {

    @Test
    void runsCallableOnVirtualThread() {
        Task<Boolean> task = async(() -> Thread.currentThread().isVirtual());
        assertTrue(await(task));
        assertTrue(task.isVirtualThread());
    }

    @Test
    void runsRunnableReturningVoidTask() {
        AtomicBoolean ran = new AtomicBoolean(false);
        Task<Void> task = async(() -> ran.set(true));
        await(task);
        assertTrue(ran.get());
    }

    @Test
    void namesTaskAndThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        Task<String> task = async("test-operation", () -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
            return "ok";
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("test-operation", task.name());
        assertTrue(threadName.get().contains("test-operation"), "Thread name should contain task name: " + threadName.get());
        assertEquals("ok", await(task));
    }

    @Test
    void tasksOverlapConcurrently() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        Task<String> task1 = async(() -> {
            bothStarted.countDown();
            release.await();
            return "one";
        });

        Task<String> task2 = async(() -> {
            bothStarted.countDown();
            release.await();
            return "two";
        });

        assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
        release.countDown();

        assertEquals("one", await(task1));
        assertEquals("two", await(task2));
    }

    @Test
    void rethrowsRuntimeExceptionDirectly() {
        Task<String> task = async(() -> {
            throw new IllegalStateException("Simulated failure");
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> await(task));
        assertEquals("Simulated failure", ex.getMessage());
    }

    @Test
    void rethrowsErrorDirectly() {
        Task<String> task = async(() -> {
            throw new OutOfMemoryError("Simulated OOM");
        });

        Error error = assertThrows(OutOfMemoryError.class, () -> await(task));
        assertEquals("Simulated OOM", error.getMessage());
    }

    @Test
    void wrapsCheckedExceptionInTaskExecutionException() {
        Task<String> task = async(() -> {
            throw new IOException("IO error");
        });

        TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> await(task));
        assertInstanceOf(IOException.class, ex.getCause());
        assertEquals("IO error", ex.getCause().getMessage());
    }

    @Test
    void handlesAwaitTimeout() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Task<String> task = async("slow-task", () -> {
            latch.await();
            return "done";
        });

        TaskTimeoutException timeoutEx = assertThrows(TaskTimeoutException.class, () -> await(task, Duration.ofMillis(50)));
        assertEquals("slow-task", timeoutEx.taskName());
        assertEquals(Duration.ofMillis(50), timeoutEx.timeout());
        assertTrue(task.isRunning());

        latch.countDown();
        assertEquals("done", await(task));
    }

    @Test
    void cancelsTaskInterruptingVirtualThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        Task<String> task = async(() -> {
            started.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
            return "never";
        });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(task.cancel());

        assertTrue(task.isCancelled());
        assertTrue(task.isDone());

        assertThrows(CancellationException.class, task::await);
    }

    @Test
    void taskScopeCancelsUnfinishedChildTasksOnClose() throws Exception {
        CountDownLatch childStarted = new CountDownLatch(1);
        AtomicBoolean childInterrupted = new AtomicBoolean(false);

        Task<String> childTask;
        try (TaskScope scope = VT.scope()) {
            childTask = scope.async(() -> {
                childStarted.countDown();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    childInterrupted.set(true);
                }
                return "result";
            });
            assertTrue(childStarted.await(2, TimeUnit.SECONDS));
        }

        assertTrue(childTask.isCancelled());
    }

    @Test
    void taskScopeRejectsForeignTask() {
        try (TaskScope scope1 = VT.scope();
             TaskScope scope2 = VT.scope()) {
            Task<String> foreignTask = scope1.async(() -> "foreign");
            assertThrows(IllegalArgumentException.class, () -> scope2.await(foreignTask));
        }
    }

    @Test
    void scopedOperationExecutesAndCleansUp() {
        String result = VT.scoped(scope -> {
            Task<String> task1 = scope.async(() -> "hello");
            Task<String> task2 = scope.async(() -> "world");
            return scope.await(task1) + " " + scope.await(task2);
        });
        assertEquals("hello world", result);
    }

    @Test
    void customAsyncRuntimeWithTaskDecoratorPropagatesContext() {
        ThreadLocal<String> context = new ThreadLocal<>();
        context.set("user-123");

        TaskDecorator decorator = runnable -> {
            String captured = context.get();
            return () -> {
                String previous = context.get();
                context.set(captured);
                try {
                    runnable.run();
                } finally {
                    context.set(previous);
                }
            };
        };

        AsyncRuntime customRuntime = AsyncRuntime.builder()
                .threadNamePrefix("custom-")
                .taskDecorator(decorator)
                .build();

        Task<String> task = customRuntime.async(() -> context.get());
        assertEquals("user-123", customRuntime.await(task));
    }
}
