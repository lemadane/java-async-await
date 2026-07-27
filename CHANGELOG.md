# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0-alpha.2] - 2026-07-28

### Added
- **Task Lifecycle State Machine**: Added a controlled internal state machine inside `Task` (`CREATED`, `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`) to ensure robust execution and prevention of duplicate starts.
- **TaskInterruptedException**: Introduced a dedicated unchecked exception thrown when the awaiting thread itself is interrupted.
- **awaitAndCancel helper**: Introduced `awaitAndCancel(task, timeout)` method to support explicitly cancelling a task when its await times out.
- **Default Spring Context Propagation**: Autoconfigured default context decorators for MDC, Spring Security, request context, and locales, composed in a deterministic order.
- **TaskScope Leak Prevention**: Finished tasks are now removed from internal tracking as soon as they complete and are awaited, preventing memory growth.
- **Multi-Module Publishing**: Centrally configured Maven Central compatible publishing with signing, sources/javadocs jars, POM metadata, and local publishing support (`publishToMavenLocal`).
- **POM validation**: Added a Gradle task `validatePom` to verify that generated POM files contain all required metadata.
- **Stress Testing**: Added a Gradle task `stressTest` running JUnit 5 tests tagged with `stress` to verify concurrency correctness under high-load scenarios.
- **JMH Benchmarks**: Added a new `benchmarks` module using JMH to compare direct virtual threads vs library primitives.

### Fixed
- **TaskScope Race Condition**: Resolved the race condition between task submission and scope closing by synchronizing state checking and task registration under a `ReentrantLock`.
- **TaskScope Deadlock**: Prevented deadlocks when a scope's child task itself calls `scope.close()` by bypassing the `await()` call for the current thread.
- **Interruption flag leak**: Fixed child task interruption incorrectly setting the interrupt flag of the awaiting thread.
- **Spring dependency bloat**: Fixed hardcoded Spring Boot version runtime constraint by using `compileOnly` scope, enabling consumer applications to manage their own Spring Boot version.
