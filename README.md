# Java Virtual Thread Async/Await Concurrency Library (`vt-async-await`)

A production-ready, framework-neutral Java library providing virtual-thread `async`/`await` concurrency primitives for standard Java 21+ applications.

---

## Requirements

- **Build JDK**: JDK 25 (or JDK 21+)
- **Runtime Environment**: Java 21 or newer
- **Bytecode Target**: Java 21 (`options.release = 21`)
- **Preview Features**: None required

---

## Installation

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation 'io.lemonade:vt-async-await:0.1.0-alpha.1'
}
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.lemonade:vt-async-await:0.1.0-alpha.1")
}
```

### Maven

```xml
<dependency>
    <groupId>io.lemonade</groupId>
    <artifactId>vt-async-await</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

### Spring Boot Starter

```groovy
dependencies {
    implementation 'io.lemonade:vt-async-await-spring-boot-starter:0.1.0-alpha.1'
}
```

---

## Quick Start

```java
import static io.lemonade.vtasyncawait.VT.async;
import static io.lemonade.vtasyncawait.VT.await;

import io.lemonade.vtasyncawait.Task;

public class CustomerDashboard {

    public Dashboard loadCustomerDashboard(String id) {
        // Immediate submission on virtual threads
        Task<Customer> customerTask = async(() -> customerService.findRequired(id));
        Task<List<Order>> ordersTask = async(() -> orderService.findForCustomer(id));

        // Await results
        Customer customer = await(customerTask);
        List<Order> orders = await(ordersTask);

        return new Dashboard(customer, orders);
    }
}
```

---

## Parallel Operations

Every task submitted via `async(...)` begins execution immediately on a new virtual thread:

```java
// Both operations start in parallel before the first await
Task<Customer> customerTask = async(() -> customerService.findRequired(id));
Task<List<Order>> ordersTask = async(() -> orderService.findForCustomer(id));

Customer customer = await(customerTask);
List<Order> orders = await(ordersTask);
```

> [!WARNING]
> Immediate sequential calls like `VT.await(VT.async(() -> service.load()))` provide no concurrency benefit. Run asynchronous operations in parallel before calling `await`.

---

## Named Tasks

Give tasks logical names for improved thread dumps, debugging, profiling, and diagnostics:

```java
Task<Customer> customerTask = async(
        "load-customer",
        () -> customerService.findRequired(id)
);
```

The underlying virtual thread will be named `vt-task-load-customer-1`.

---

## Timeouts

Set maximum wait durations when awaiting tasks:

```java
try {
    Customer customer = VT.await(customerTask, Duration.ofSeconds(2));
} catch (TaskTimeoutException e) {
    System.out.println("Task timed out: " + e.taskName() + " after " + e.timeout());
}
```

> [!NOTE]
> An await timeout throws `TaskTimeoutException` but does **not** automatically cancel the running task unless explicitly requested.

---

## Cancellation

Cancel tasks to interrupt their underlying virtual threads:

```java
Task<Customer> customerTask = async(() -> customerService.findRequired(id));

// Cancel execution
boolean cancelled = customerTask.cancel();

// Awaiting a cancelled task throws java.util.concurrent.CancellationException
```

---

## Task Scopes

Manage child task lifetimes deterministically without using Java preview APIs (`StructuredTaskScope`):

```java
try (TaskScope scope = VT.scope()) {
    Task<Customer> customerTask = scope.async("load-customer", () -> customerService.findRequired(id));
    Task<List<Order>> ordersTask = scope.async("load-orders", () -> orderService.findForCustomer(id));

    Customer customer = scope.await(customerTask);
    List<Order> orders = scope.await(ordersTask);

    return new Dashboard(customer, orders);
}
```

Or using `VT.scoped(...)`:

```java
Dashboard dashboard = VT.scoped(scope -> {
    Task<Customer> customerTask = scope.async(() -> customerService.findRequired(id));
    Task<List<Order>> ordersTask = scope.async(() -> orderService.findForCustomer(id));

    return new Dashboard(scope.await(customerTask), scope.await(ordersTask));
});
```

---

## Exception Handling

- **Unchecked Exceptions (`RuntimeException`)**: Rethrown directly.
- **Errors (`Error`)**: Rethrown directly.
- **Checked Exceptions**: Wrapped in `TaskExecutionException` preserving cause.
- **Await Timeout**: Throws `TaskTimeoutException`.
- **Cancellation**: Throws `java.util.concurrent.CancellationException`.
- **Interruption**: Restores interrupted flag and throws `CancellationException`.

---

## Static API vs. Injectable `AsyncRuntime`

Use `VT` for static facade convenience in plain Java code.
Use `AsyncRuntime` for dependency injection and framework integration:

```java
@Bean
public AsyncRuntime asyncRuntime() {
    return AsyncRuntime.builder()
            .threadNamePrefix("booking-task-")
            .build();
}
```

---

## Spring Boot Integration

Add `vt-async-await-spring-boot-starter` to auto-configure an `AsyncRuntime` bean:

```properties
vt.concurrent.enabled=true
vt.concurrent.thread-name-prefix=booking-task-
```

Inject `AsyncRuntime` directly into your services:

```java
@Service
public class DashboardService {
    private final AsyncRuntime asyncRuntime;

    public DashboardService(AsyncRuntime asyncRuntime) {
        this.asyncRuntime = asyncRuntime;
    }
}
```

---

## Framework Context Propagation

Use `TaskDecorator` to capture caller thread context (MDC, tracing, Security) and restore it on virtual threads:

```java
TaskDecorator decorator = operation -> {
    String mdcContext = MDC.get("requestId");
    return () -> {
        MDC.put("requestId", mdcContext);
        try {
            operation.run();
        } finally {
            MDC.remove("requestId");
        }
    };
};

AsyncRuntime runtime = AsyncRuntime.builder()
        .taskDecorator(decorator)
        .build();
```

---

## Building & Publishing

Build the library:

```bash
./gradlew build
```

Publish artifacts to Maven Local:

```bash
./gradlew publishToMavenLocal
```

---

## Limitations

- Spring transactions are bound to the creating thread. `VT.async()` does not propagate open Spring transactions across virtual thread boundaries.
- ThreadLocal context propagation must be configured explicitly via `TaskDecorator`.
