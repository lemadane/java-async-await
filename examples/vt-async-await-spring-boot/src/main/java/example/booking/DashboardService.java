package example.booking;

import io.lemadane.vt.async.await.AsyncRuntime;
import io.lemadane.vt.async.await.Task;
import io.lemadane.vt.async.await.TaskScope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class DashboardService {

    private final AsyncRuntime asyncRuntime;

    public DashboardService(AsyncRuntime asyncRuntime) {
        this.asyncRuntime = asyncRuntime;
    }

    public Dashboard load(String customerId) {
        try (TaskScope scope = this.asyncRuntime.scope()) {
            Task<String> customerTask = scope.async(
                    "load-customer",
                    () -> "Customer(" + customerId + ")"
            );

            Task<List<String>> ordersTask = scope.async(
                    "load-orders",
                    () -> List.of("Order-1", "Order-2")
            );

            return new Dashboard(
                    scope.await(customerTask),
                    scope.await(ordersTask)
            );
        }
    }

    public record Dashboard(String customer, List<String> orders) {}
}
