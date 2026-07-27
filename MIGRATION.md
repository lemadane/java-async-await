# Migration Guide

This document describes the changes required when migrating from `0.1.0-alpha.1` to `0.1.0-alpha.2`.

## 1. Exception Handling for Awaiting Interruption

### Old Behavior
When the thread awaiting a task was interrupted, the library threw a `CancellationException` with the `InterruptedException` as the cause.

### New Behavior
The library now throws a dedicated `io.lemadane.vt.async.await.TaskInterruptedException` (unchecked), which preserves the original `InterruptedException` as its cause and restores the thread's interrupt flag.

### Migration Action
Update your exception catching blocks to expect `TaskInterruptedException` instead of `CancellationException` when dealing with awaiting thread interruptions:

```java
// Before
try {
    VT.await(task);
} catch (CancellationException e) {
    if (e.getCause() instanceof InterruptedException) {
        // Handle awaiting thread interruption
    }
}

// After
try {
    VT.await(task);
} catch (TaskInterruptedException e) {
    // Handle awaiting thread interruption
}
```

---

## 2. Await Timeouts and Task Cancellation

### Old Behavior
Awaiting a task with a timeout (`VT.await(task, timeout)`) only stopped waiting (throwing `TaskTimeoutException`) and left the child task running.

### New Behavior
The default behavior remains the same (stopping waiting without cancelling). However, we have added a new helper `awaitAndCancel(task, timeout)` which explicitly cancels the task if it times out.

### Migration Action
If you want the task to be cancelled on timeout, migrate from:

```java
// Before
try {
    VT.await(task, timeout);
} catch (TaskTimeoutException e) {
    task.cancel(true);
    throw e;
}

// After
VT.awaitAndCancel(task, timeout);
```

---

## 3. Spring Boot Starter Dependency Version Control

### Old Behavior
The Spring Boot Starter hardcoded Spring Boot version `3.3.5` as a transitive runtime dependency.

### New Behavior
The Spring Boot version is now marked as `compileOnly` inside the autoconfigure module and completely omitted from the starter module, allowing the consuming application's dependency management to control it.

### Migration Action
Make sure your application specifies a compatible Spring Boot version (e.g. via parent POM or BOM plugins), as the library starter will no longer force `3.3.5`.
