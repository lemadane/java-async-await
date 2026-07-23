package example;

import io.lemadane.vt.async.await.AsyncRuntime;
import io.lemadane.vt.async.await.Task;
import io.lemadane.vt.async.await.TaskScope;

import static io.lemadane.vt.async.await.VT.async;
import static io.lemadane.vt.async.await.VT.await;

public class JavaAsyncExample {

    public static String loadCustomerData(String customerId) {
        Task<String> profileTask = async("profile-task", () -> "Profile(" + customerId + ")");
        Task<String> ordersTask = async("orders-task", () -> "Orders(" + customerId + ")");

        return await(profileTask) + " + " + await(ordersTask);
    }

    public static String loadWithCustomRuntime(String customerId) {
        AsyncRuntime customRuntime = AsyncRuntime.builder()
                .threadNamePrefix("booking-task-")
                .build();

        try (TaskScope scope = customRuntime.scope()) {
            Task<String> customerTask = scope.async("load-customer", () -> "Customer(" + customerId + ")");
            Task<String> pointsTask = scope.async("load-points", () -> "Points(100)");

            return scope.await(customerTask) + " + " + scope.await(pointsTask);
        }
    }

    public static void main(String[] args) {
        System.out.println("Customer data: " + loadCustomerData("C123"));
        System.out.println("Custom runtime data: " + loadWithCustomRuntime("C123"));
    }
}
