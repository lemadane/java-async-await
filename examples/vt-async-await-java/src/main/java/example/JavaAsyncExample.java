package example;

import vt.async.await.AggregateException;
import vt.async.await.AsyncRuntime;
import vt.async.await.AsyncTask;
import vt.async.await.AsyncTaskScope;
import vt.async.await.VT;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static vt.async.await.VT.async;
import static vt.async.await.VT.await;

/**
 * Example demonstrating usages of Java VT Async/Await library combinator APIs and helper utilities.
 */
public class JavaAsyncExample {

    /**
     * Loads customer data sequentially using static VT.async and VT.await.
     *
     * @param customerId the customer ID
     * @return combined customer data
     */
    public static String loadCustomerData(String customerId) {
        AsyncTask<String> profileTask = async("profile-task", () -> "Profile(" + customerId + ")");
        AsyncTask<String> ordersTask = async("orders-task", () -> "Orders(" + customerId + ")");

        return await(profileTask) + " + " + await(ordersTask);
    }

    /**
     * Loads customer data within a custom AsyncRuntime and AsyncTaskScope.
     *
     * @param customerId the customer ID
     * @return combined customer data
     */
    public static String loadWithCustomRuntime(String customerId) {
        AsyncRuntime customRuntime = AsyncRuntime.builder()
                .threadNamePrefix("booking-task-")
                .build();

        try (AsyncTaskScope scope = customRuntime.scope()) {
            AsyncTask<String> customerTask = scope.async("load-customer", () -> "Customer(" + customerId + ")");
            AsyncTask<String> pointsTask = scope.async("load-points", () -> "Points(100)");

            return scope.await(customerTask) + " + " + scope.await(pointsTask);
        }
    }

    /**
     * Demonstrates VT.scoped functional lambda wrapper.
     *
     * @param customerId the customer ID
     * @return customer profile string
     */
    public static String loadScopedLambda(String customerId) {
        return VT.scoped(scope -> {
            AsyncTask<String> profileTask = scope.async("load-profile", () -> "Profile(" + customerId + ")");
            return scope.await(profileTask);
        });
    }

    /**
     * Demonstrates task.onComplete callback registration.
     *
     * @param customerId customer ID
     * @param callback completion listener
     * @return profile string
     */
    public static String loadWithOnCompleteCallback(String customerId, Runnable callback) {
        AsyncTask<String> task = async(() -> "Profile(" + customerId + ")");
        task.onComplete(callback);
        return await(task);
    }

    /**
     * Demonstrates creating an unstarted task and starting it explicitly.
     *
     * @param customerId customer ID
     * @return profile string
     */
    public static String loadUnstartedTask(String customerId) {
        AsyncRuntime runtime = AsyncRuntime.builder().build();
        AsyncTask<String> unstarted = runtime.createUnstartedTask("lazy-task", () -> "LazyProfile(" + customerId + ")");
        unstarted.start();
        return runtime.await(unstarted);
    }

    /**
     * Demonstrates VT.all combinator to await all tasks concurrently.
     *
     * @param customerId the customer ID
     * @return list of results from all tasks
     */
    public static List<String> loadAllServices(String customerId) {
        AsyncTask<String> p1 = async(() -> "Profile(" + customerId + ")");
        AsyncTask<String> p2 = async(() -> "Orders(" + customerId + ")");
        AsyncTask<String> p3 = async(() -> "Preferences(" + customerId + ")");

        return VT.all(Arrays.asList(p1, p2, p3));
    }

    /**
     * Demonstrates VT.race combinator to return whichever task settles first.
     *
     * @return the result of the winning task
     */
    public static String loadFastestProvider() {
        AsyncTask<String> slow = async(() -> {
            Thread.sleep(1000);
            return "Slow Provider";
        });
        AsyncTask<String> fast = async(() -> "Fast Provider");

        return VT.race(Arrays.asList(slow, fast));
    }

    /**
     * Demonstrates VT.any combinator to return the first successful task.
     *
     * @return result of the first successful task
     */
    public static String loadAnyAvailableProvider() {
        AsyncTask<String> failing = async(() -> {
            throw new RuntimeException("Primary provider down");
        });
        AsyncTask<String> backup = async(() -> "Backup Provider");

        return VT.any(Arrays.asList(failing, backup));
    }

    /**
     * Demonstrates VT.allSettled to inspect results of all tasks regardless of individual state.
     *
     * @return collection of settled tasks
     */
    public static Collection<AsyncTask<? extends String>> loadSettledSummary() {
        AsyncTask<String> success = async(() -> "OK");
        AsyncTask<String> fail = async(() -> {
            throw new IllegalStateException("Error");
        });
        AsyncTask<String> cancelled = async(() -> {
            Thread.sleep(5000);
            return "Never";
        });
        cancelled.cancel(true);

        return VT.allSettled(Arrays.asList(success, fail, cancelled));
    }

    /**
     * Demonstrates VT.awaitAndCancel to enforce a strict timeout on a task.
     *
     * @param customerId the customer ID
     * @param timeout max duration to wait
     * @return customer profile string
     */
    public static String loadWithStrictTimeout(String customerId, Duration timeout) {
        AsyncTask<String> task = async(() -> "Profile(" + customerId + ")");
        return VT.awaitAndCancel(task, timeout);
    }

    /**
     * Main entry point for standalone execution.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.out.println("Customer data: " + loadCustomerData("C123"));
        System.out.println("Custom runtime data: " + loadWithCustomRuntime("C123"));
        System.out.println("Scoped lambda data: " + loadScopedLambda("C123"));
        System.out.println("All services: " + loadAllServices("C123"));
        System.out.println("Fastest provider: " + loadFastestProvider());
        System.out.println("Any available: " + loadAnyAvailableProvider());
        System.out.println("Settled count: " + loadSettledSummary().size());
    }
}
