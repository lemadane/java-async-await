package example.booking;

import vt.async.await.AsyncRuntime;
import vt.async.await.AsyncTask;
import vt.async.await.AsyncTaskScope;
import vt.async.await.VT;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Spring service demonstrating VT Async/Await combinators in a Spring Boot environment.
 */
@Service
public final class DashboardService {

    private final AsyncRuntime asyncRuntime;

    /**
     * Constructor injecting autoconfigured AsyncRuntime.
     *
     * @param asyncRuntime the spring-managed AsyncRuntime
     */
    public DashboardService(AsyncRuntime asyncRuntime) {
        this.asyncRuntime = asyncRuntime;
    }

    /**
     * Loads dashboard data concurrently using scope.
     *
     * @param customerId the customer ID
     * @return the dashboard data
     */
    public Dashboard load(String customerId) {
        try (AsyncTaskScope scope = this.asyncRuntime.scope()) {
            AsyncTask<String> customerTask = scope.async(
                    "load-customer",
                    () -> "Customer(" + customerId + ")"
            );

            AsyncTask<List<String>> ordersTask = scope.async(
                    "load-orders",
                    () -> List.of("Order-1", "Order-2")
            );

            return new Dashboard(
                    scope.await(customerTask),
                    scope.await(ordersTask)
            );
        }
    }

    /**
     * Demonstrates VT.scoped functional lambda wrapper in Spring.
     *
     * @param customerId customer ID
     * @return customer payload
     */
    public String loadScopedLambda(String customerId) {
        return VT.scoped(scope -> {
            AsyncTask<String> task = scope.async(() -> "Customer(" + customerId + ")");
            return scope.await(task);
        });
    }

    /**
     * Demonstrates task.onComplete callback registration.
     *
     * @param customerId customer ID
     * @param callback completion listener
     * @return customer payload
     */
    public String loadWithCallback(String customerId, Runnable callback) {
        AsyncTask<String> task = this.asyncRuntime.async(() -> "Customer(" + customerId + ")");
        task.onComplete(callback);
        return this.asyncRuntime.await(task);
    }

    /**
     * Demonstrates creating and manually starting an unstarted task.
     *
     * @param customerId customer ID
     * @return customer payload
     */
    public String loadLazyTask(String customerId) {
        AsyncTask<String> unstarted = this.asyncRuntime.createUnstartedTask("lazy", () -> "Customer(" + customerId + ")");
        unstarted.start();
        return this.asyncRuntime.await(unstarted);
    }

    /**
     * Loads multiple service details using scope.all.
     *
     * @param customerId the customer ID
     * @return list of service responses
     */
    public List<String> loadAll(String customerId) {
        try (AsyncTaskScope scope = this.asyncRuntime.scope()) {
            AsyncTask<String> t1 = scope.async(() -> "Customer(" + customerId + ")");
            AsyncTask<String> t2 = scope.async(() -> "Orders(" + customerId + ")");
            return scope.all(Arrays.asList(t1, t2));
        }
    }

    /**
     * Loads fastest recommendation using scope.race.
     *
     * @return fastest recommendation response
     */
    public String loadFastestRecommendation() {
        try (AsyncTaskScope scope = this.asyncRuntime.scope()) {
            AsyncTask<String> slow = scope.async(() -> {
                Thread.sleep(1000);
                return "Slow ML Model";
            });
            AsyncTask<String> fast = scope.async(() -> "Fast Rules Engine");
            return scope.race(Arrays.asList(slow, fast));
        }
    }

    /**
     * Loads first available payment gateway using scope.any.
     *
     * @return first successful gateway result
     */
    public String loadAnyPaymentGateway() {
        try (AsyncTaskScope scope = this.asyncRuntime.scope()) {
            AsyncTask<String> primary = scope.async(() -> {
                throw new RuntimeException("Primary Gateway Timeout");
            });
            AsyncTask<String> secondary = scope.async(() -> "Secondary Gateway OK");
            return scope.any(Arrays.asList(primary, secondary));
        }
    }

    /**
     * Loads settled audit tasks using scope.allSettled.
     *
     * @return collection of settled tasks
     */
    public Collection<AsyncTask<? extends String>> loadSettledAudit() {
        try (AsyncTaskScope scope = this.asyncRuntime.scope()) {
            AsyncTask<String> t1 = scope.async(() -> "Audit-Log-1");
            AsyncTask<String> t2 = scope.async(() -> {
                throw new IllegalStateException("Audit-Log-2-Failed");
            });
            return scope.allSettled(Arrays.asList(t1, t2));
        }
    }

    /**
     * Awaits task with strict timeout and cancels on expiration.
     *
     * @param customerId customer ID
     * @param timeout duration timeout
     * @return customer profile string
     */
    public String loadWithTimeoutAndCancel(String customerId, Duration timeout) {
        try (AsyncTaskScope scope = this.asyncRuntime.scope()) {
            AsyncTask<String> task = scope.async(() -> "Customer(" + customerId + ")");
            return scope.awaitAndCancel(task, timeout);
        }
    }

    /**
     * Record representing Dashboard state.
     *
     * @param customer customer info
     * @param orders list of orders
     */
    public record Dashboard(String customer, List<String> orders) {}
}
