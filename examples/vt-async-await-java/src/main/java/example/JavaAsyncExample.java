package example;

import vt.async.await.AsyncRuntime;
import vt.async.await.AsyncTask;
import vt.async.await.AsyncTaskScope;

import static vt.async.await.VT.async;
import static vt.async.await.VT.await;

public class JavaAsyncExample {

    public static String loadCustomerData(String customerId) {
        AsyncTask<String> profileTask = async("profile-task", () -> "Profile(" + customerId + ")");
        AsyncTask<String> ordersTask = async("orders-task", () -> "Orders(" + customerId + ")");

        return await(profileTask) + " + " + await(ordersTask);
    }

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

    public static void main(String[] args) {
        System.out.println("Customer data: " + loadCustomerData("C123"));
        System.out.println("Custom runtime data: " + loadWithCustomRuntime("C123"));
    }
}
