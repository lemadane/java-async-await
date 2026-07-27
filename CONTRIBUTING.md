# Contributing to vt-async-await

Thank you for your interest in contributing to `vt-async-await`! We welcome bug reports, feature suggestions, and pull requests.

## Development Setup

### Requirements
- **JDK**: Java 21 or newer (Java 25 recommended for local builds)
- **Gradle**: Configured wrapper included

### Build and Run Tests
To compile the codebase and run all unit and integration tests:
```bash
./gradlew clean check
```

### Run Concurrency Stress Tests
We have stress tests to verify race conditions under high concurrency. To execute them:
```bash
./gradlew stressTest -PstressIterations=5000
```

### Run Benchmarks
To compile and run the JMH performance benchmarks:
```bash
./gradlew benchmarks:benchmark
```

## Pull Request Guidelines

1. **Keep it Dependency-Light**: Do not add third-party dependencies to the core module.
2. **Follow Concurrency Principles**: Ensure all operations are thread-safe and avoid holding locks over blocking calls (e.g. while waiting for task completions).
3. **No Preview Features**: Keep the production source free from Java preview features.
4. **Write Tests**: Every fix or feature must include unit tests and/or stress tests where applicable.
