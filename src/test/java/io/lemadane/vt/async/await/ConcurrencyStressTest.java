package io.lemadane.vt.async.await;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Tag("stress")
class ConcurrencyStressTest {

    private static int getIterations() {
        return Integer.getInteger("stressIterations", 5000);
    }

    @Test
    void testScopeAsyncCloseRace() throws Exception {
        int iterations = getIterations();
        for (int i = 0; i < iterations; i++) {
            TaskScope scope = VT.scope();
            CountDownLatch startClose = new CountDownLatch(1);
            Thread closer = Thread.ofVirtual().start(() -> {
                try {
                    startClose.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                scope.close();
            });

            Thread submitter = Thread.ofVirtual().start(() -> {
                try {
                    scope.async(() -> "val");
                } catch (IllegalStateException e) {
                    // Expected if scope is already closed/closing
                }
            });

            startClose.countDown();
            closer.join();
            submitter.join();
        }
    }

    @Test
    void testCancelCompleteRace() throws Exception {
        int iterations = getIterations();
        for (int i = 0; i < iterations; i++) {
            CountDownLatch started = new CountDownLatch(1);
            Task<String> task = VT.async(() -> {
                started.countDown();
                return "result";
            });
            Thread canceller = Thread.ofVirtual().start(() -> {
                task.cancel(true);
            });
            try {
                task.await();
            } catch (CancellationException ce) {
                // Expected if cancelled
            }
            canceller.join();
        }
    }

    @Test
    void testTimeoutCompleteRace() throws Exception {
        int iterations = getIterations();
        for (int i = 0; i < iterations; i++) {
            Task<String> task = VT.async(() -> "result");
            Thread timeouts = Thread.ofVirtual().start(() -> {
                try {
                    VT.await(task, Duration.ofNanos(1));
                } catch (TaskTimeoutException e) {
                    // Expected
                }
            });
            try {
                VT.await(task);
            } catch (CancellationException ce) {
                // Expected
            }
            timeouts.join();
        }
    }

    @Test
    void testThousandsOfShortTasks() throws Exception {
        int count = getIterations();
        Task<?>[] tasks = new Task<?>[count];
        for (int i = 0; i < count; i++) {
            tasks[i] = VT.async(() -> "val");
        }
        for (int i = 0; i < count; i++) {
            assertEquals("val", VT.await(tasks[i]));
        }
    }

    @Test
    void testConcurrentCallsToAwait() throws Exception {
        int count = 100;
        Task<String> task = VT.async(() -> {
            Thread.sleep(50);
            return "val";
        });
        CountDownLatch latch = new CountDownLatch(count);
        AtomicBoolean success = new AtomicBoolean(true);
        for (int i = 0; i < count; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    if (!"val".equals(VT.await(task))) {
                        success.set(false);
                    }
                } catch (Exception e) {
                    success.set(false);
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(success.get());
    }

    @Test
    void testRuntimeShutdownDuringSubmission() throws Exception {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        int count = 1000;
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Thread closer = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            runtime.close();
            shutdownLatch.countDown();
        });

        for (int i = 0; i < count; i++) {
            try {
                runtime.async(() -> "val");
            } catch (IllegalStateException e) {
                // Expected after shutdown
            }
        }
        closer.join();
    }

    @Test
    void testContextIsolationUnderHighConcurrency() throws Exception {
        int count = getIterations();
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        TaskDecorator decorator = op -> {
            String context = threadLocal.get();
            return () -> {
                String prev = threadLocal.get();
                threadLocal.set(context);
                try {
                    op.run();
                } finally {
                    threadLocal.set(prev);
                }
            };
        };
        AsyncRuntime runtime = AsyncRuntime.builder()
                .taskDecorator(decorator)
                .build();

        Task<?>[] tasks = new Task<?>[count];
        for (int i = 0; i < count; i++) {
            threadLocal.set("context-" + i);
            final int index = i;
            tasks[i] = runtime.async(() -> {
                assertEquals("context-" + index, threadLocal.get());
                return "ok";
            });
        }
        threadLocal.remove();

        for (int i = 0; i < count; i++) {
            assertEquals("ok", runtime.await(tasks[i]));
        }
        runtime.close();
    }

    @Test
    void testCancelFailRace() throws Exception {
        int iterations = getIterations();
        for (int i = 0; i < iterations; i++) {
            CountDownLatch started = new CountDownLatch(1);
            Task<String> task = VT.async(() -> {
                started.countDown();
                throw new RuntimeException("fail");
            });
            Thread canceller = Thread.ofVirtual().start(() -> {
                task.cancel(true);
            });
            try {
                task.await();
            } catch (RuntimeException ce) {
                // Expected either cancellation or runtime exception
            }
            canceller.join();
        }
    }

    @Test
    void testListenerRegistrationCompletionRace() throws Exception {
        int iterations = getIterations();
        for (int i = 0; i < iterations; i++) {
            AsyncRuntime runtime = AsyncRuntime.builder().build();
            Task<String> task = runtime.createUnstartedTask("race", () -> "ok");

            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
            CountDownLatch listenerLatch = new CountDownLatch(1);

            Thread registrationThread = Thread.ofVirtual().start(() -> {
                task.onComplete(() -> {
                    count.incrementAndGet();
                    listenerLatch.countDown();
                });
            });

            Thread completionThread = Thread.ofVirtual().start(() -> {
                task.start();
            });

            registrationThread.join();
            completionThread.join();

            try {
                task.await();
            } catch (Exception e) {
                // Ignore exceptions during race
            }

            assertTrue(listenerLatch.await(5, TimeUnit.SECONDS), "Listener did not execute in time");
            assertEquals(1, count.get(), "Listener must run exactly once");
            runtime.close();
        }
    }

    @Test
    void testDecoratorFailureCancellationRace() throws Exception {
        int iterations = getIterations();
        for (int i = 0; i < iterations; i++) {
            LifecycleAndListenerTest.TestDecorator decorator = new LifecycleAndListenerTest.TestDecorator();
            decorator.setupFailed.set(true);
            AsyncRuntime runtime = AsyncRuntime.builder().taskDecorator(decorator).build();

            Task<String> task = runtime.createUnstartedTask("race", () -> "ok");

            Thread canceller = Thread.ofVirtual().start(() -> {
                task.cancel(true);
            });

            Thread starter = Thread.ofVirtual().start(() -> {
                task.start();
            });

            canceller.join();
            starter.join();

            try {
                task.await();
            } catch (RuntimeException ex) {
                // Expected
            }

            assertTrue(task.isDone());
            runtime.close();
        }
    }

    @Test
    void testAwaitedTaskStatusInstantConsistency() throws Exception {
        int iterations = getIterations();
        for (int i = 0; i < iterations; i++) {
            AsyncRuntime runtime = AsyncRuntime.builder().build();
            Task<String> task = runtime.async(() -> "value");

            assertEquals("value", runtime.await(task));

            // Immediately check status
            assertTrue(task.isDone(), "task.isDone() must be true immediately after await returns");
            assertNotEquals(Task.State.RUNNING, task.lifecycleState(), "task state must not be RUNNING after await returns");
            assertNotEquals(Task.State.CREATED, task.lifecycleState(), "task state must not be CREATED after await returns");

            runtime.close();
        }
    }
}
