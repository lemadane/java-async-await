package example;

import io.lemonade.vtasyncawait.AsyncRuntime;
import io.lemonade.vtasyncawait.Task;
import io.lemonade.vtasyncawait.TaskScope;

import static io.lemonade.vtasyncawait.VT.async;
import static io.lemonade.vtasyncawait.VT.await;

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
