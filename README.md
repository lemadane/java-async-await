# Java Virtual Thread Async/Await Concurrency Library

Switching virtual-threads in your Spring Boot application is not enough. Try Java-Async-Await, A framework-neutral Java library providing virtual-thread `async`/`await` concurrency primitives for standard Java 21+ applications.

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

> [!NOTE]
> **Pre-release & Local Testing**: `0.1.0-beta.1` is currently in pre-release state. To test before the release tag workflow finishes publishing to Maven Central, run `./gradlew publishToMavenLocal` and include `mavenLocal()` in your repositories block.

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation 'vt.async.await:vt-async-await:0.1.0-beta.1'
}
```

### Maven

```xml
<dependency>
    <groupId>vt.async.await</groupId>
    <artifactId>vt-async-await</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>
```

---

## Quick Start (Plain Java)

```java
import static vt.async.await.VT.async;
import static vt.async.await.VT.await;

import vt.async.await.AsyncTask;

public class CustomerDashboard {

    public Dashboard loadCustomerDashboard(String id) {
        // Immediate parallel submission on virtual threads
        AsyncTask<Customer> customerTask = async(() -> customerService.findRequired(id));
        AsyncTask<List<Order>> ordersTask = async(() -> orderService.findForCustomer(id));

        // Await results
        Customer customer = await(customerTask);
        List<Order> orders = await(ordersTask);

        return new Dashboard(customer, orders);
    }
}
```

---

## Complete API Summary List

Here is the master quick-reference list of all public APIs provided by `vt.async.await`:

### 1. Static Facade (`vt.async.await.VT`)
| API Method | Summary | Detailed Link |
| :--- | :--- | :--- |
| `VT.async(Callable<T>)` | Submits a value-returning operation on a virtual thread. | [Details & Example](#1-task-creation--execution) |
| `VT.async(String name, Callable<T>)` | Submits a named value-returning operation. | [Details & Example](#1-task-creation--execution) |
| `VT.async(Runnable)` | Submits a side-effect operation on a virtual thread. | [Details & Example](#1-task-creation--execution) |
| `VT.async(String name, Runnable)` | Submits a named side-effect operation. | [Details & Example](#1-task-creation--execution) |
| `VT.await(AsyncTask<T>)` | Cheaply blocks until task finishes and unwraps result. | [Details & Example](#2-awaiting--timeout-control) |
| `VT.await(AsyncTask<T>, Duration)` | Awaits up to timeout; throws `AsyncTaskTimeoutException` (no cancel). | [Details & Example](#2-awaiting--timeout-control) |
| `VT.awaitAndCancel(AsyncTask<T>, Duration)` | Awaits up to timeout and **cancels** task on expiration. | [Details & Example](#2-awaiting--timeout-control) |
| `VT.scope()` | Creates a new structured `AsyncTaskScope` (`try-with-resources`). | [Details & Example](#3-structured-concurrency-scopes) |
| `VT.scoped(ScopedOperation<T>)` | Executes operation inside an auto-closing scope. | [Details & Example](#3-structured-concurrency-scopes) |
| `VT.all(Collection<AsyncTask<T>>)` | Fail-fast parallel all; returns `List<T>` or throws on error. | [Details & Example](#4-promise-combinators) |
| `VT.any(Collection<AsyncTask<T>>)` | Returns first successful result; throws `AggregateException` if all fail. | [Details & Example](#4-promise-combinators) |
| `VT.race(Collection<AsyncTask<T>>)` | Returns/rethrows outcome of whichever task settles first. | [Details & Example](#4-promise-combinators) |
| `VT.allSettled(Collection<AsyncTask<T>>)` | Awaits all tasks to complete (success, fail, cancel) without throwing. | [Details & Example](#4-promise-combinators) |

### 2. Task Handle (`vt.async.await.AsyncTask<T>`)
| API Method | Summary | Detailed Link |
| :--- | :--- | :--- |
| `task.start()` | Starts execution if task is in `CREATED` state. | [Details & Example](#1-task-creation--execution) |
| `task.onComplete(Runnable)` | Registers an exactly-once completion listener. | [Details & Example](#5-task-lifecycle--completion-callbacks) |
| `task.lifecycleState()` | Returns internal `AsyncTask.State` (`CREATED`, `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`). | [Details & Example](#5-task-lifecycle--completion-callbacks) |
| `task.state()` | Overrides JDK `Future.state()`. | [Details & Example](#5-task-lifecycle--completion-callbacks) |
| `task.cancel()` / `task.cancel(boolean)` | Cancels execution and interrupts virtual thread. | [Details & Example](#5-task-lifecycle--completion-callbacks) |
| `task.isRunning()` | Returns `true` if thread is currently running. | [Details & Example](#5-task-lifecycle--completion-callbacks) |
| `task.isVirtualThread()` | Returns `true` if executing on a virtual thread. | [Details & Example](#5-task-lifecycle--completion-callbacks) |
| `task.name()` | Returns logical name of task. | [Details & Example](#5-task-lifecycle--completion-callbacks) |

### 3. Structured Scope (`vt.async.await.AsyncTaskScope`)
| API Method | Summary | Detailed Link |
| :--- | :--- | :--- |
| `scope.async(...)` | Spawns a task bound to scope lifecycle. | [Details & Example](#3-structured-concurrency-scopes) |
| `scope.await(task)` / `scope.await(task, timeout)` | Awaits a task within scope. | [Details & Example](#3-structured-concurrency-scopes) |
| `scope.awaitAndCancel(task, timeout)` | Awaits up to timeout and cancels task on expiration. | [Details & Example](#3-structured-concurrency-scopes) |
| `scope.all(...)` / `scope.any(...)` / `scope.race(...)` / `scope.allSettled(...)` | Scope-bound combinators. | [Details & Example](#4-promise-combinators) |
| `scope.close()` | Auto-closes scope, joining active child tasks. | [Details & Example](#3-structured-concurrency-scopes) |

### 4. Custom Runtime & Context Decorators (`vt.async.await.AsyncRuntime`)
| API Method | Summary | Detailed Link |
| :--- | :--- | :--- |
| `AsyncRuntime.builder()` | Configures thread prefix, decorators, exception handlers, or executors. | [Details & Example](#6-custom-runtime-configuration--context-decorators) |
| `runtime.createUnstartedTask(...)` | Instantiates unstarted task in `CREATED` state. | [Details & Example](#1-task-creation--execution) |
| `AsyncTaskDecorator.decorate(Runnable)` | Captures context on caller thread and restores on virtual thread. | [Details & Example](#6-custom-runtime-configuration--context-decorators) |

---

## Using in Spring Boot Applications

### Step 1: Add Spring Boot Starter Dependency

**Gradle (Groovy):**
```groovy
dependencies {
    implementation 'vt.async.await:vt-async-await-spring-boot-starter:0.1.0-beta.1'
}
```

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>vt.async.await</groupId>
    <artifactId>vt-async-await-spring-boot-starter</artifactId>
    <version>0.1.0-beta.1</version>
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
        try (AsyncTaskScope scope = asyncRuntime.scope()) {
            AsyncTask<CustomerDto> customerTask = scope.async("load-customer", 
                    () -> customerClient.fetchCustomer(customerId));
            
            AsyncTask<List<OrderDto>> ordersTask = scope.async("load-orders", 
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

If you need to configure your own custom `AsyncTaskDecorator`, define it as a `@Bean`:
```java
@Configuration
public class AsyncConfig {
    @Bean
    public AsyncTaskDecorator customAsyncTaskDecorator() {
        return operation -> {
            // custom context capture and restore...
            return operation;
        };
    }
}
```
If a custom `AsyncTaskDecorator` bean is present, the default composite autoconfiguration backs off.

---

## API Feature Reference

This section provides a complete reference of all API functions, their purpose, detailed explanations, and code examples.

### 1. Task Creation & Execution

#### `VT.async(Callable<T>)` / `VT.async(String name, Callable<T>)`
* **Description**: Submits a value-returning operation (`Callable`) for immediate execution on a virtual thread.
* **Use Case**: Used for initiating asynchronous background computation that produces a result.
* **Code Example**:
  ```java
  AsyncTask<Customer> task = VT.async("fetch-customer", () -> customerService.find(id));
  ```

#### `VT.async(Runnable)` / `VT.async(String name, Runnable)`
* **Description**: Submits a side-effect operation (`Runnable`) for immediate execution on a virtual thread.
* **Use Case**: Used for fire-and-forget or side-effect tasks (e.g. audit logging, sending notifications).
* **Code Example**:
  ```java
  AsyncTask<Void> auditTask = VT.async("audit-log", () -> auditLogger.logEvent("login", userId));
  ```

#### `runtime.createUnstartedTask(String name, Callable<T>/Runnable)` & `task.start()`
* **Description**: Instantiates a task in the `CREATED` state without immediately starting its thread execution. Calling `.start()` triggers execution.
* **Use Case**: Lazy initialization or deferred execution where task creation and execution timing must be decoupled.
* **Code Example**:
  ```java
  AsyncTask<String> lazyTask = runtime.createUnstartedTask("lazy-task", () -> computeData());
  // Later in execution flow:
  lazyTask.start();
  String result = runtime.await(lazyTask);
  ```

---

### 2. Awaiting & Timeout Control

#### `VT.await(AsyncTask<T>)`
* **Description**: Cheaply blocks the current thread until the task completes and unwraps the result.
* **Use Case**: Primary method for consuming an asynchronous task's output.
* **Code Example**:
  ```java
  AsyncTask<User> userTask = VT.async(() -> userService.get(id));
  User user = VT.await(userTask);
  ```

#### `VT.await(AsyncTask<T>, Duration timeout)`
* **Description**: Awaits a task up to a specified maximum duration. If the duration elapses before completion, throws `AsyncTaskTimeoutException` **without** cancelling the background task.
* **Use Case**: Soft timeouts where you want to stop waiting on the caller thread but allow the background task to finish.
* **Code Example**:
  ```java
  try {
      Data data = VT.await(task, Duration.ofSeconds(2));
  } catch (AsyncTaskTimeoutException e) {
      System.out.println("Wait timed out, task is still running in background");
  }
  ```

#### `VT.awaitAndCancel(AsyncTask<T>, Duration timeout)`
* **Description**: Awaits a task up to a specified duration and **automatically cancels** the task if the timeout elapses.
* **Use Case**: Hard timeouts where an uncompleted background task is useless and should be terminated to free resources.
* **Code Example**:
  ```java
  try {
      Data data = VT.awaitAndCancel(task, Duration.ofSeconds(2));
  } catch (AsyncTaskTimeoutException e) {
      System.out.println("Wait timed out and background task was cancelled!");
  }
  ```

---

### 3. Structured Concurrency Scopes

#### `VT.scope()` / `try (AsyncTaskScope scope = VT.scope())`
* **Description**: Creates a new structured concurrency scope (`AutoCloseable`). All tasks spawned in the scope are bound to its lifecycle.
* **Use Case**: Resource management ensuring child tasks are joined or cleaned up when leaving a block.
* **Code Example**:
  ```java
  try (AsyncTaskScope scope = VT.scope()) {
      AsyncTask<Customer> t1 = scope.async(() -> loadCustomer(id));
      AsyncTask<Orders> t2 = scope.async(() -> loadOrders(id));
      return new Summary(scope.await(t1), scope.await(t2));
  }
  ```

#### `VT.scoped(ScopedOperation<T>)`
* **Description**: Functional lambda wrapper that manages opening, executing, and closing an `AsyncTaskScope` automatically.
* **Use Case**: Clean, single-expression functional scoping.
* **Code Example**:
  ```java
  CustomerSummary summary = VT.scoped(scope -> {
      AsyncTask<Customer> t1 = scope.async(() -> loadCustomer(id));
      AsyncTask<Orders> t2 = scope.async(() -> loadOrders(id));
      return new CustomerSummary(scope.await(t1), scope.await(t2));
  });
  ```

---

### 4. Promise Combinators

#### `VT.all(Collection<AsyncTask<T>>)`
* **Description**: Awaits all tasks. If any task fails, it immediately **fails-fast**, cancels all other tasks in the collection, and throws `AsyncTaskExecutionException`.
* **Use Case**: Parallel execution where all sub-operations are strictly required.
* **Code Example**:
  ```java
  List<String> results = VT.all(Arrays.asList(
      VT.async(() -> fetchServiceA()),
      VT.async(() -> fetchServiceB())
  ));
  ```

#### `VT.any(Collection<AsyncTask<T>>)`
* **Description**: Awaits and returns the result of the **first task that succeeds**. If all tasks fail, throws `AggregateException` containing all failures.
* **Use Case**: Redundant fail-over requests across multiple providers or mirrors.
* **Code Example**:
  ```java
  String data = VT.any(Arrays.asList(
      VT.async(() -> fetchPrimaryMirror()),
      VT.async(() -> fetchSecondaryMirror())
  ));
  ```

#### `VT.race(Collection<AsyncTask<T>>)`
* **Description**: Awaits and returns the outcome (value or exception) of whichever task **settles first** (succeeds, fails, or cancels), cancelling remaining tasks.
* **Use Case**: Performance benchmarking or competitive execution across algorithms.
* **Code Example**:
  ```java
  String fastest = VT.race(Arrays.asList(
      VT.async(() -> algorithmA()),
      VT.async(() -> algorithmB())
  ));
  ```

#### `VT.allSettled(Collection<AsyncTask<T>>)`
* **Description**: Awaits all tasks to complete (whether success, failure, or cancellation) without throwing exceptions, returning the settled task instances.
* **Use Case**: Bulk processing or audit logging where individual failures should be inspected rather than aborting.
* **Code Example**:
  ```java
  Collection<AsyncTask<? extends String>> settled = VT.allSettled(tasks);
  for (AsyncTask<? extends String> task : settled) {
      if (task.lifecycleState() == AsyncTask.State.SUCCESS) {
          System.out.println("Result: " + VT.await(task));
      } else {
          System.out.println("State: " + task.lifecycleState());
      }
  }
  ```

---

### 5. Task Lifecycle & Completion Callbacks

#### `task.onComplete(Runnable listener)`
* **Description**: Registers an exactly-once callback listener triggered when the task reaches a terminal state. Runs immediately if the task is already completed.
* **Use Case**: Reactive notifications, logging, or metrics recording upon task completion.
* **Code Example**:
  ```java
  AsyncTask<Order> task = VT.async(() -> processOrder(id));
  task.onComplete(() -> metrics.increment("orders.processed"));
  ```

#### `task.lifecycleState()` / `task.state()`
* **Description**: Queries the current state (`CREATED`, `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`).
* **Use Case**: Non-blocking status inspection.
* **Code Example**:
  ```java
  if (task.lifecycleState() == AsyncTask.State.RUNNING) {
      System.out.println("Task is still processing...");
  }
  ```

#### `task.cancel()` / `task.cancel(boolean mayInterruptIfRunning)`
* **Description**: Cancels execution of the task and interrupts its virtual thread if running.
* **Use Case**: Manual cancellation when results are no longer required.
* **Code Example**:
  ```java
  boolean cancelled = task.cancel(true);
  ```

#### `task.isRunning()` / `task.isVirtualThread()` / `task.name()`
* **Description**: Diagnostic helpers to inspect running state, virtual thread backing, and task name.
* **Code Example**:
  ```java
  System.out.println("Task " + task.name() + " isVirtual=" + task.isVirtualThread());
  ```

---

### 6. Custom Runtime Configuration & Context Decorators

#### `AsyncRuntime.builder()`
* **Description**: Configures custom runtime settings: `threadNamePrefix`, `taskDecorator`, `uncaughtExceptionHandler`, and `executorService`.
* **Use Case**: Application customization for custom thread naming, error handling, or context propagation.
* **Code Example**:
  ```java
  AsyncRuntime runtime = AsyncRuntime.builder()
      .threadNamePrefix("my-app-worker-")
      .taskDecorator(myDecorator)
      .build();
  ```

#### `AsyncTaskDecorator` / `.andThen(...)`
* **Description**: Functional interface for capturing caller context (e.g. MDC, Security, Request Attributes) on the submitting thread and restoring/cleaning up on the virtual thread.
* **Use Case**: Context propagation across thread boundaries.
* **Code Example**:
  ```java
  AsyncTaskDecorator mdcDecorator = operation -> {
      Map<String, String> callerMdc = MDC.getCopyOfContextMap();
      return () -> {
          MDC.setContextMap(callerMdc);
          try {
              operation.run();
          } finally {
              MDC.clear();
          }
      };
  };
  ```

---

## Features & Usage

### Timeouts

We distinguish between two timeout modes:
- **Await timeout only** (keeps task running):
  ```java
  try {
      Customer customer = VT.await(task, Duration.ofSeconds(2));
  } catch (AsyncTaskTimeoutException e) {
      System.out.println("Wait timed out, but task is still running!");
  }
  ```
- **Await and cancel on timeout**:
  ```java
  try {
      Customer customer = VT.awaitAndCancel(task, Duration.ofSeconds(2));
  } catch (AsyncTaskTimeoutException e) {
      System.out.println("Wait timed out and task was cancelled!");
  }
  ```

### Cancellation

Tasks can be cancelled, which transitions their state to `CANCELLED` and interrupts their thread if running:
```java
AsyncTask<Customer> task = async(() -> customerService.findRequired(id));
boolean cancelled = task.cancel(true); // returns true if transitioned to CANCELLED
```

### AsyncTask State & Lifecycle

Every `AsyncTask` is backed by a `ManagedFutureTask` which serves as the **single authoritative completion source**. The task goes through a strict state machine represented by the `AsyncTask.State` enum:
- `CREATED`: AsyncTask instantiated but not yet started (e.g. unstarted tasks).
- `RUNNING`: Virtual thread started and currently executing the task operation.
- `SUCCESS`: AsyncTask completed successfully returning a value (or null).
- `FAILED`: AsyncTask completed with an exception.
- `CANCELLED`: AsyncTask was explicitly cancelled.

#### Guarantees

- **Authoritative Future Truth**: The task's lifecycle transition occurs inside the underlying `FutureTask` completion callback (`done()`), matching the future's terminal outcome.
- **Single Terminal State Transition**: A task transitions to exactly one terminal state (`SUCCESS`, `FAILED`, or `CANCELLED`) exactly once. No terminal state may transition to any other state (e.g., calling `cancel()` after completion returns `false` and leaves the state unchanged).
- **Exactly-Once Listeners**: Completion listeners execute at most once, only after reaching a terminal state. Listeners registered before completion run upon completion, and those registered after completion run immediately. No lock is held during listener callback execution.
- **Decorator Correctness**: The entire execution chain (decorator setup, user operation execution, decorator cleanup/restore) is wrapped in the future's callable.
  - If decorator setup or cleanup fails, the task transitions to `FAILED` and propagates the error.
  - If both user execution and decorator cleanup fail, the user exception is thrown as the primary exception, and the cleanup exception is added as a suppressed exception.
- **Thread Safety**: Multiple threads may await/query the same task concurrently and safely.

You can inspect the state in two ways:
- **Internal State**: `task.lifecycleState()` returns `AsyncTask.State`.
- **JDK 21 standard**: `task.state()` overrides `Future.state()` and maps the internal state to the standard Java `java.util.concurrent.Future.State` enum.

### AsyncTaskScope Concurrency & Safety

The library's `AsyncTaskScope` is a lightweight, non-preview alternative to Java's structured concurrency `StructuredAsyncTaskScope`. It offers:
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
} catch (AsyncTaskExecutionException e) {
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
Collection<AsyncTask<? extends String>> settled = VT.allSettled(Arrays.asList(
  VT.async(() -> loadUsers()),
  VT.async(() -> { throw new RuntimeException("Failed endpoint"); })
));

for (AsyncTask<? extends String> task : settled) {
  if (task.lifecycleState() == AsyncTask.State.SUCCESS) {
    System.out.println("Success: " + VT.await(task));
  } else {
    System.out.println("Failed: " + task.lifecycleState());
  }
}
```

---

## Exception & Interruption Semantics

- **Unchecked Exceptions / Errors**: Propagated directly to the awaiting thread without double-wrapping.
- **Checked Exceptions**: Wrapped in `AsyncTaskExecutionException` preserving the original cause.
- **Awaiting-Thread Interruption**: Restores the awaiting thread's interrupt flag and throws `AsyncTaskInterruptedException`.
- **Child-AsyncTask Interruption**: Wrapped in `AsyncTaskExecutionException` as a standard checked exception; does NOT set the interrupt flag of the awaiting thread.

---

## Design Philosophy & Limitations

### Why Blocking is Preferred Over Callback Chaining

Unlike traditional asynchronous programming in Java (e.g. `CompletableFuture`), this library does not support callback chaining (like `.then()` or `.catch()`). This is by design: with Java 21 Virtual Threads, blocking is extremely cheap. Instead of writing complex reactive/callback chains, you can write clean, sequential Java code:

```java
// Avoid complex callback chaining (e.g. CompletableFuture style):
AsyncTask<String> task = VT.async(() -> loadData())
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
