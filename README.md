# Java Virtual Thread Async/Await Concurrency Library

A production-ready, framework-neutral Java library providing virtual-thread `async`/`await` concurrency primitives for standard Java 21+ applications.

---

## Requirements

- **Build JDK**: JDK 25 (or JDK 21+)
- **Runtime Environment**: Java 21 or newer
- **Bytecode Target**: Java 21 (`options.release = 21`)
- **Preview Features**: None required

---

## Installation

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation 'io.lemonade:vt-async-await:0.1.0-alpha.1'
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

---

## Quick Start (Plain Java)

```java
import static io.lemonade.vtasyncawait.VT.async;
import static io.lemonade.vtasyncawait.VT.await;

import io.lemonade.vtasyncawait.Task;

public class CustomerDashboard {

    public Dashboard loadCustomerDashboard(String id) {
        // Immediate parallel submission on virtual threads
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

## Using in Spring Boot Applications

### Step 1: Add Spring Boot Starter Dependency

**Gradle (Groovy):**
```groovy
dependencies {
    implementation 'io.lemonade:vt-async-await-spring-boot-starter:0.1.0-alpha.1'
}
```

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>io.lemonade</groupId>
    <artifactId>vt-async-await-spring-boot-starter</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

---

### Step 2: Configure Properties (`application.properties` or `application.yml`)

```properties
vt.concurrent.enabled=true
vt.concurrent.thread-name-prefix=booking-task-
```

---

### Step 3: Inject `AsyncRuntime` into Spring Components

The starter automatically provides an `AsyncRuntime` bean in the application context.

#### Spring `@Service` Example:

```java
package com.example.service;

import io.lemonade.vtasyncawait.AsyncRuntime;
import io.lemonade.vtasyncawait.Task;
import io.lemonade.vtasyncawait.TaskScope;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DashboardService {

    private final AsyncRuntime asyncRuntime;
    private final CustomerClient customerClient;
    private final OrderClient orderClient;

    public DashboardService(AsyncRuntime asyncRuntime, 
                            CustomerClient customerClient, 
                            OrderClient orderClient) {
        this.asyncRuntime = asyncRuntime;
        this.customerClient = customerClient;
        this.orderClient = orderClient;
    }

    public DashboardResponse getDashboard(String customerId) {
        // Use a structured scope to bind task execution to this block
        try (TaskScope scope = asyncRuntime.scope()) {
            Task<CustomerDto> customerTask = scope.async("load-customer", 
                    () -> customerClient.fetchCustomer(customerId));
            
            Task<List<OrderDto>> ordersTask = scope.async("load-orders", 
                    () -> orderClient.fetchOrders(customerId));

            // Await both results concurrently
            CustomerDto customer = scope.await(customerTask);
            List<OrderDto> orders = scope.await(ordersTask);

            return new DashboardResponse(customer, orders);
        }
    }
}
```

#### Spring `@RestController` Example:

```java
package com.example.controller;

import com.example.service.DashboardService;
import com.example.dto.DashboardResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{customerId}")
    public DashboardResponse getDashboard(@PathVariable String customerId) {
        return dashboardService.getDashboard(customerId);
    }
}
```

---

### Step 4: Configure Thread Context Propagation (MDC / Tracing)

In Spring Boot, log trace IDs and MDC context are often set on the request handling thread. Configure a `@Bean TaskDecorator` to propagate caller thread context to virtual threads automatically:

```java
package com.example.config;

import io.lemonade.vtasyncawait.TaskDecorator;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class AsyncConfig {

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return operation -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                try {
                    operation.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
```

---

## Features & Usage

### Parallel Operations
Every task submitted via `async(...)` begins execution immediately on a new virtual thread:

```java
Task<Customer> customerTask = async(() -> customerService.findRequired(id));
Task<List<Order>> ordersTask = async(() -> orderService.findForCustomer(id));

Customer customer = await(customerTask);
List<Order> orders = await(ordersTask);
```

### Named Tasks
Logical naming helps with thread dumps, profiling, and diagnostics:

```java
Task<Customer> customerTask = async("load-customer", () -> customerService.findRequired(id));
```

### Timeouts
```java
try {
    Customer customer = VT.await(customerTask, Duration.ofSeconds(2));
} catch (TaskTimeoutException e) {
    System.out.println("Task timed out: " + e.taskName() + " after " + e.timeout());
}
```

### Cancellation
```java
Task<Customer> customerTask = async(() -> customerService.findRequired(id));
boolean cancelled = customerTask.cancel();
```

---

## Exception Handling

- **Unchecked Exceptions (`RuntimeException`)**: Rethrown directly.
- **Errors (`Error`)**: Rethrown directly.
- **Checked Exceptions**: Wrapped in `TaskExecutionException` preserving the original cause.
- **Await Timeout**: Throws `TaskTimeoutException`.
- **Cancellation**: Throws `java.util.concurrent.CancellationException`.

---

## Building & Local Publishing

Build the library:
```bash
./gradlew build
```

Publish artifacts to Maven Local:
```bash
./gradlew publishToMavenLocal
```

---

## Publishing to GitHub

To push this repository to GitHub:

```bash
git init
git add .
git commit -m "Initial commit of vt-async-await library"
git branch -M main
git remote add origin git@github.com:YOUR_USERNAME/vt-async-await.git
git push -u origin main
```
