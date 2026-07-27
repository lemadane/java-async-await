# Java Virtual Thread Async/Await Concurrency Library

A framework-neutral Java library providing virtual-thread `async`/`await` concurrency primitives for standard Java 21+ applications.

Inspired by async/await syntax in other languages, this library does NOT introduce new Java language keywords. Instead, `await(task)` is a blocking operation on the current thread (which is extremely cheap on virtual threads).

> [!NOTE]
> **Project Maturity**: This project is currently in the **Beta** phase. All race and stress tests pass, but api signatures are subject to minor tweaks before stable release.

---

## Requirements

- **Build JDK**: JDK 21 or newer (Java 25 recommended)
- **Runtime Environment**: Java 21 or newer
- **Bytecode Target**: Java 21 (`options.release = 21`)
- **Preview Features**: None required (standard non-preview API)

---

## Installation

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation 'io.lemadane:vt-async-await:0.1.0-alpha.2'
}
```

### Maven

```xml
<dependency>
    <groupId>io.lemadane</groupId>
    <artifactId>vt-async-await</artifactId>
    <version>0.1.0-alpha.2</version>
</dependency>
```

---

## Quick Start (Plain Java)

```java
import static io.lemadane.vt.async.await.VT.async;
import static io.lemadane.vt.async.await.VT.await;

import io.lemadane.vt.async.await.Task;

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
    implementation 'io.lemadane:vt-async-await-spring-boot-starter:0.1.0-alpha.2'
}
```

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>io.lemadane</groupId>
    <artifactId>vt-async-await-spring-boot-starter</artifactId>
    <version>0.1.0-alpha.2</version>
</dependency>
```

### Step 2: Configure Properties (`application.properties` or `application.yml`)

```properties
vt.concurrent.enabled=true
vt.concurrent.thread-name-prefix=booking-task-
```

### Step 3: Inject `AsyncRuntime` into Spring Components

The starter automatically provides an `AsyncRuntime` bean in the application context.

```java
@Service
public class DashboardService {

    private final AsyncRuntime asyncRuntime;

    public DashboardService(AsyncRuntime asyncRuntime) {
        this.asyncRuntime = asyncRuntime;
    }

    public DashboardResponse getDashboard(String customerId) {
        try (TaskScope scope = asyncRuntime.scope()) {
            Task<CustomerDto> customerTask = scope.async("load-customer", 
                    () -> customerClient.fetchCustomer(customerId));
            
            Task<List<OrderDto>> ordersTask = scope.async("load-orders", 
                    () -> orderClient.fetchOrders(customerId));

            // Await both results
            CustomerDto customer = scope.await(customerTask);
            List<OrderDto> orders = scope.await(ordersTask);

            return new DashboardResponse(customer, orders);
        }
    }
}
```

### Step 4: Automatic Thread Context Propagation

The starter automatically detects and configures context decorators in a deterministic order:
1. **LocaleContext** (`LocaleContextHolder`)
2. **MDC** (`org.slf4j.MDC`)
3. **RequestAttributes** (`RequestContextHolder`)
4. **SecurityContext** (`SecurityContextHolder`)

If you need to configure your own custom `TaskDecorator`, define it as a `@Bean`:
```java
@Configuration
public class AsyncConfig {
    @Bean
    public TaskDecorator customTaskDecorator() {
        return operation -> {
            // custom context capture and restore...
            return operation;
        };
    }
}
```
If a custom `TaskDecorator` bean is present, the default composite autoconfiguration backs off.

---

## Features & Usage

### Timeouts

We distinguish between two timeout modes:
- **Await timeout only** (keeps task running):
  ```java
  try {
      Customer customer = VT.await(task, Duration.ofSeconds(2));
  } catch (TaskTimeoutException e) {
      System.out.println("Wait timed out, but task is still running!");
  }
  ```
- **Await and cancel on timeout**:
  ```java
  try {
      Customer customer = VT.awaitAndCancel(task, Duration.ofSeconds(2));
  } catch (TaskTimeoutException e) {
      System.out.println("Wait timed out and task was cancelled!");
  }
  ```

### Cancellation

Tasks can be cancelled, which transitions their state to `CANCELLED` and interrupts their thread if running:
```java
Task<Customer> task = async(() -> customerService.findRequired(id));
boolean cancelled = task.cancel(true); // returns true if transitioned to CANCELLED
```

### Task State & Lifecycle

Every `Task` is backed by a `ManagedFutureTask` which serves as the **single authoritative completion source**. The task goes through a strict state machine represented by the `Task.State` enum:
- `CREATED`: Task instantiated but not yet started (e.g. unstarted tasks).
- `RUNNING`: Virtual thread started and currently executing the task operation.
- `SUCCESS`: Task completed successfully returning a value (or null).
- `FAILED`: Task completed with an exception.
- `CANCELLED`: Task was explicitly cancelled.

#### Guarantees

- **Authoritative Future Truth**: The task's lifecycle transition occurs inside the underlying `FutureTask` completion callback (`done()`), matching the future's terminal outcome.
- **Single Terminal State Transition**: A task transitions to exactly one terminal state (`SUCCESS`, `FAILED`, or `CANCELLED`) exactly once. No terminal state may transition to any other state (e.g., calling `cancel()` after completion returns `false` and leaves the state unchanged).
- **Exactly-Once Listeners**: Completion listeners execute at most once, only after reaching a terminal state. Listeners registered before completion run upon completion, and those registered after completion run immediately. No lock is held during listener callback execution.
- **Decorator Correctness**: The entire execution chain (decorator setup, user operation execution, decorator cleanup/restore) is wrapped in the future's callable.
  - If decorator setup or cleanup fails, the task transitions to `FAILED` and propagates the error.
  - If both user execution and decorator cleanup fail, the user exception is thrown as the primary exception, and the cleanup exception is added as a suppressed exception.
- **Thread Safety**: Multiple threads may await/query the same task concurrently and safely.

You can inspect the state in two ways:
- **Internal State**: `task.lifecycleState()` returns `Task.State`.
- **JDK 21 standard**: `task.state()` overrides `Future.state()` and maps the internal state to the standard Java `java.util.concurrent.Future.State` enum.

### TaskScope Concurrency & Safety

The library's `TaskScope` is a lightweight, non-preview alternative to Java's structured concurrency `StructuredTaskScope`. It offers:
- **Atomic Operations**: Submissions and scope closure are fully synchronized under a lock. Tasks will never leak or start after the scope transitions to closed.
- **Deadlock-Free Self-Close**: If a task running inside a scope calls `scope.close()` (for example, on a panic/fail-fast path), it does not deadlock waiting for itself.
- **Automatic Leak Prevention**: Completed child tasks are auto-removed from the scope's internal tracking, preventing unbounded memory growth.

### Resource Ownership

- **Library-owned executor**: If `AsyncRuntime` creates the executor (or runs directly on virtual threads), the runtime closes it when `close()` is called.
- **Caller-provided executor**: If you supply your own `ExecutorService` via the builder:
  ```java
  AsyncRuntime runtime = AsyncRuntime.builder()
      .executorService(myExecutor, false) // false means runtime does NOT own it
      .build();
  ```
  The caller remains responsible for shutting down their own executor.

### Promise Combinators (JavaScript-like Coordination)

The library provides static facade and scope-bound combinators equivalent to JavaScript's Promise helpers.

#### 1. `all` vs `Promise.all()`

* **Concept**: Awaits all tasks. If *any* task fails or is cancelled, it immediately fails-fast, cancels all other tasks in the collection, and propagates the failure. Returns the list of results in the input order.
* **Semantic Difference**: JavaScript's `Promise.all()` rejects immediately but does *not* cancel the other running promises (since standard JavaScript Promises are not cancellable). Java's `VT.all()` automatically cancels and interrupts all other tasks in the collection upon failure to prevent resource leakage on virtual threads.

##### JavaScript Example:
```javascript
try {
  const results = await Promise.all([
    fetch('/api/users'),
    fetch('/api/products')
  ]);
  console.log(results);
} catch (error) {
  console.error("One of the requests failed", error);
}
```

##### Java Example:
```java
try {
  List<Object> results = VT.all(Arrays.asList(
    VT.async(() -> userService.loadUsers()),
    VT.async(() -> productService.loadProducts())
  ));
  System.out.println(results);
} catch (TaskExecutionException e) {
  System.err.println("One of the tasks failed: " + e.getCause());
}
```

---

#### 2. `any` vs `Promise.any()`

* **Concept**: Awaits and returns the result of the first task that successfully completes. If all tasks fail, throws an `AggregateException` containing all individual failures.

##### JavaScript Example:
```javascript
try {
  const fastestSuccess = await Promise.any([
    fetchFromMirrorA(),
    fetchFromMirrorB()
  ]);
  console.log(fastestSuccess);
} catch (aggregateError) {
  console.error("All mirrors failed", aggregateError.errors);
}
```

##### Java Example:
```java
try {
  String fastestSuccess = VT.any(Arrays.asList(
    VT.async(() -> fetchFromMirrorA()),
    VT.async(() -> fetchFromMirrorB())
  ));
  System.out.println(fastestSuccess);
} catch (AggregateException e) {
  System.err.println("All tasks failed:");
  e.getCauses().forEach(System.err::println);
}
```

---

#### 3. `race` vs `Promise.race()`

* **Concept**: Awaits and returns the result or exception of the first task that completes (either succeeds, fails, or cancels), and cancels all other tasks in the set.

##### JavaScript Example:
```javascript
try {
  const winner = await Promise.race([
    fetchData(),
    delay(2000).then(() => { throw new Error("Timeout"); })
  ]);
  console.log(winner);
} catch (error) {
  console.error("Failed or timed out first", error);
}
```

##### Java Example:
```java
try {
  String winner = VT.race(Arrays.asList(
    VT.async(() -> fetchData()),
    VT.async(() -> {
      Thread.sleep(2000);
      throw new RuntimeException("Timeout");
    })
  ));
  System.out.println(winner);
} catch (RuntimeException e) {
  System.err.println("Failed or timed out first: " + e.getMessage());
}
```

---

#### 4. `allSettled` vs `Promise.allSettled()`

* **Concept**: Awaits all tasks to complete (succeed, fail, or cancel) without throwing exceptions, returning them for outcome inspection.

##### JavaScript Example:
```javascript
const outcomes = await Promise.allSettled([
  fetch('/api/users'),
  fetch('/api/bad-url')
]);

outcomes.forEach(outcome => {
  if (outcome.status === 'fulfilled') {
    console.log("Success:", outcome.value);
  } else {
    console.log("Failed:", outcome.reason);
  }
});
```

##### Java Example:
```java
Collection<Task<? extends String>> settled = VT.allSettled(Arrays.asList(
  VT.async(() -> loadUsers()),
  VT.async(() -> { throw new RuntimeException("Failed endpoint"); })
));

for (Task<? extends String> task : settled) {
  if (task.lifecycleState() == Task.State.SUCCESS) {
    System.out.println("Success: " + VT.await(task));
  } else {
    System.out.println("Failed: " + task.lifecycleState());
  }
}
```

---

## Exception & Interruption Semantics

- **Unchecked Exceptions / Errors**: Propagated directly to the awaiting thread without double-wrapping.
- **Checked Exceptions**: Wrapped in `TaskExecutionException` preserving the original cause.
- **Awaiting-Thread Interruption**: Restores the awaiting thread's interrupt flag and throws `TaskInterruptedException`.
- **Child-Task Interruption**: Wrapped in `TaskExecutionException` as a standard checked exception; does NOT set the interrupt flag of the awaiting thread.

---

## Design Philosophy & Limitations

### Why Blocking is Preferred Over Callback Chaining

Unlike traditional asynchronous programming in Java (e.g. `CompletableFuture`), this library does not support callback chaining (like `.then()` or `.catch()`). This is by design: with Java 21 Virtual Threads, blocking is extremely cheap. Instead of writing complex reactive/callback chains, you can write clean, sequential Java code:

```java
// Avoid complex callback chaining (e.g. CompletableFuture style):
Task<String> task = VT.async(() -> loadData())
                      .thenApply(data -> process(data))
                      .exceptionally(err -> fallback());

// Prefer clean sequential blocking syntax on a Virtual Thread:
VT.async(() -> {
    try {
        String data = loadData();
        return process(data);
    } catch (Exception e) {
        return fallback();
    }
});
```

### Limitations
- `await(task)` blocks the awaiting thread. While blocking a virtual thread is very inexpensive, blocking a platform thread can block the carrier thread if not handled correctly.
- This library does not add asynchronous callbacks or reactives; it is intended for clean sequential blocking syntax in Java.

---

## Build & Test Instructions

Build project:
```bash
./gradlew build
```

Run tests:
```bash
./gradlew test
```

Run stress tests:
```bash
./gradlew stressTest -PstressIterations=10000
```

Run JMH benchmarks:
```bash
./gradlew benchmarks:benchmark
```

Local publishing:
```bash
./gradlew publishToMavenLocal
```
