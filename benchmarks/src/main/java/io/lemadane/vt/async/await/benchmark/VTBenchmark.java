package io.lemadane.vt.async.await.benchmark;

import io.lemadane.vt.async.await.AsyncRuntime;
import io.lemadane.vt.async.await.Task;
import io.lemadane.vt.async.await.TaskScope;
import io.lemadane.vt.async.await.VT;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark comparing direct virtual thread creation, static VT facade,
 * AsyncRuntime, and TaskScope.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class VTBenchmark {

    private AsyncRuntime runtime;

    /**
     * Initializes the benchmark state.
     */
    @Setup
    public void setup() {
        runtime = AsyncRuntime.builder().build();
    }

    /**
     * Tears down the benchmark state.
     */
    @TearDown
    public void tearDown() {
        runtime.close();
    }

    /**
     * Measures raw virtual thread creation and joining overhead.
     */
    @Benchmark
    public void directVirtualThread() throws InterruptedException {
        Thread thread = Thread.ofVirtual().start(() -> {});
        thread.join();
    }

    /**
     * Measures async submission and awaiting using the static VT facade.
     */
    @Benchmark
    public Object staticVTAsyncAwait() {
        Task<String> task = VT.async(() -> "result");
        return VT.await(task);
    }

    /**
     * Measures async submission and awaiting using the AsyncRuntime.
     */
    @Benchmark
    public Object runtimeAsyncAwait() {
        Task<String> task = runtime.async(() -> "result");
        return runtime.await(task);
    }

    /**
     * Measures scoped async submission and awaiting within an AutoCloseable TaskScope.
     */
    @Benchmark
    public Object scopedAsyncAwait() {
        try (TaskScope scope = runtime.scope()) {
            Task<String> task = scope.async(() -> "result");
            return scope.await(task);
        }
    }
}
