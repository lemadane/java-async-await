# Jatot

[![CI](https://github.com/lemadane/jatot-lang/actions/workflows/ci.yml/badge.svg)](https://github.com/lemadane/jatot-lang/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)

**Jatot** is a Java-compatible language that adds practical features Java developers often wish Java already had—without making the language feel unfamiliar.

**Pronunciation:** *Jato* — the final `t` is silent.

The name comes from **TOTAL** seen in a mirror: the mirrored `L` resembles a `J`, turning **TOTAL** into **JATOT**. It also represents the **total set of practical features Java should have**.

> **Jatot is Java reflected, refined, and completed.**

## Project status

Jatot is currently in the **compiler-foundation stage**.

The repository already contains:

- a Java 21 Gradle project
- a **lexer** for Jatot keywords and operators
- a **parser** producing a full abstract syntax tree
- **semantic analysis** with symbol resolution and type checking
- a **lowering** pass that transforms Jatot AST constructs to Java-compatible forms
- a **Java source emitter** for transpilation output
- source-file and diagnostic abstractions
- a CLI with `check`, `tokens`, and `checkJatot` commands
- a Virtual Thread runtime prototype for `async`/`await` lowering
- JUnit 5 tests (including end-to-end compiler tests)
- an example `.jatot` source file

The compiler pipeline is functional from lexing through Java emission. Work continues on expanding language feature coverage and hardening the transpilation output.

## Design goal

A Java developer should be able to read Jatot immediately.

Jatot keeps Java as the baseline:

- classes, interfaces, records, enums, and annotations
- packages and imports
- constructors and method overloading
- generics and Java collections
- checked and unchecked exceptions
- Java libraries and frameworks
- JVM bytecode and the standard Java runtime

Jatot adds focused syntax while preserving Java-shaped code.

## A first look

```java
@Service
public class DashboardService {
    private final CustomerService! customerService;
    private final OrderService! orderService;

    public DashboardService(
            CustomerService! customerService,
            OrderService! orderService) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    public Dashboard! load(UUID! customerId) {
        final customerFuture =
            async this.customerService.findRequired(customerId);

        final ordersFuture =
            async this.orderService.findForCustomer(customerId);

        final customer = await customerFuture;
        final orders = await ordersFuture;

        final status = if (customer.isActive()) {
            yield "ACTIVE";
        } else {
            yield "INACTIVE";
        };

        final city =
            customer.address()?.city() ?? "Unknown";

        final names = for (Order! order : orders) {
            yield order.name().normalized();
        };

        return new Dashboard(customer, city, status, names);
    }
}
```

## Confirmed language features

### Nullable and non-null reference types

Reference types are nullable by default:

```java
String text;
Customer customer;
```

The `!` suffix means non-null:

```java
String! text;
Customer! customer;
```

`!` does **not** mean non-empty. Empty strings, lists, and arrays remain valid non-null values.

```java
String! message = "";
List<Integer>! numbers = new ArrayList<>();
int[]! values = {};
```

### Optional chaining

```java
final city = customer.address()?.city();
```

If a nullable receiver is `null`, the rest of the optional chain is skipped and the expression evaluates to `null`.

### Null coalescing

```java
final city = customer.address()?.city() ?? "Unknown";
```

The right-hand expression is evaluated only when the left-hand value is `null`.

### Immutable and mutable locals

Use `final` for an immutable local binding:

```java
final customer = this.repository.findRequired(id);
```

Use `var` for a mutable local binding:

```java
var attempts = 0;
attempts++;
```

Jatot does not use `final var`.

### Immutable parameters

Method and constructor parameters are implicitly immutable bindings:

```java
public void rename(String! name) {
    final normalizedName = name.trim();
    this.name = normalizedName;
}
```

Reassigning `name` is a compile-time error. The referenced object may still be mutable.

### Explicit `this.`

Instance field access and instance method calls inside a class must use `this.`:

```java
this.repository.save(order);
this.status = "SAVED";
this.logStatus();
```

Calls on other objects remain normal Java-style calls:

```java
customer.name();
order.calculateTotal();
```

### Boolean operators

Jatot supports seven boolean operators in both textual and symbolic form.

#### Operator table

| Textual   | Symbolic equivalent | Meaning                      |
|-----------|---------------------|------------------------------|
| `not`     | `!`                 | Logical negation             |
| `and`     | `&&`                | Short-circuit logical AND    |
| `or`      | `\|\|`              | Short-circuit logical OR     |
| `nand`    | —                   | Negated logical AND          |
| `nor`     | —                   | Negated logical OR           |
| `xor`     | —                   | Exclusive OR                 |
| `xnor`    | —                   | Logical equivalence          |

The existing symbolic operators `!`, `&&`, and `||` remain fully supported and may be freely mixed with textual operators:

```java
boolean allowed = active && not suspended;
boolean visible = enabled and !hidden;
boolean accepted = valid || trusted or administrator;
```

#### Operator semantics

| Expression     | Java equivalent        |
|----------------|------------------------|
| `not a`        | `!a`                   |
| `a and b`      | `a && b`               |
| `a or b`       | `a \|\| b`             |
| `a nand b`     | `!(a && b)`            |
| `a nor b`      | `!(a \|\| b)`          |
| `a xor b`      | `a != b`               |
| `a xnor b`     | `a == b`               |

#### Truth table

| A     | B     | AND   | OR    | NAND  | NOR   | XOR   | XNOR  |
|-------|-------|-------|-------|-------|-------|-------|-------|
| false | false | false | false | true  | true  | false | true  |
| false | true  | false | true  | true  | false | true  | false |
| true  | false | false | true  | true  | false | true  | false |
| true  | true  | true  | true  | false | false | false | true  |

#### Operator precedence (highest to lowest)

1. Parentheses
2. Unary `!` and `not`
3. `&&`, `and`, `nand`
4. `xor`, `xnor`
5. `||`, `or`, `nor`

Binary operators at the same level are left-associative:

```java
not a and b          // parsed as: (not a) and b
a or b and c         // parsed as: a or (b and c)
a xor b and c        // parsed as: a xor (b and c)
a nand b or c        // parsed as: (a nand b) or c
```

#### Short-circuit behavior

`and` and `or` have the same short-circuit semantics as `&&` and `||`.
`nand` and `nor` also short-circuit where possible:

```java
false nand expensiveCheck()   // expensiveCheck() is NOT called — result is always true
true  nor  expensiveCheck()   // expensiveCheck() is NOT called — result is always false
```

`xor` and `xnor` must evaluate both operands because their result depends on both values.

#### Type checking

All seven textual boolean operators are **boolean-only**. Using them on non-boolean values is a compile-time error:

```java
// Valid
boolean result = ready and available;

// Invalid — produces a compile error:
// Operator 'and' requires boolean operands, but found int and int.
int result = 10 and 20;
```

#### Reserved keywords

The following identifiers are reserved as boolean operator keywords and may not be used as variable, method, or type names:

```text
not  and  or  nand  nor  xor  xnor
```

Note: `notification`, `android`, `ordinary`, `xorValue`, etc. remain valid identifiers because keywords are only matched on exact word boundaries.

#### Realistic examples

```java
public boolean canAccess(
        boolean authenticated,
        boolean suspended,
        boolean administrator) {

    return authenticated and not suspended or administrator;
}
```

```java
public boolean exactlyOneSelected(
        boolean emailSelected,
        boolean smsSelected) {

    return emailSelected xor smsSelected;
}
```

```java
public boolean haveSameStatus(
        boolean firstActive,
        boolean secondActive) {

    return firstActive xnor secondActive;
}
```

> **Note:** `xor` and `xnor` are Boolean-only operators. A future integer bitwise XOR operator, if added, will use a different symbol or keyword to avoid ambiguity.

### `if` expression

```java
final status = if (order.isPaid()) {
    yield "PAID";
} else {
    yield "PENDING";
};
```

Every reachable branch must yield a compatible result.

### `try` expression

```java
final port = try {
    yield Integer.parseInt(portText);
} catch (NumberFormatException exception) {
    yield 8080;
};
```

### `for` expression

Jatot uses Java's existing `for` keyword. It does not introduce `foreach`.

Enhanced `for` expression:

```java
final names = for (Customer! customer : customers) {
    yield customer.name();
};
```

Traditional `for` expression:

```java
final numbers = for (var i = 0; i < 10; i++) {
    yield i;
};
```

Each executed `yield` contributes an element to the resulting collection.

### `while` and `do-while` expressions

```java
final values = while (iterator.hasNext()) {
    yield iterator.next();
};
```

```java
final values = do {
    yield this.readValue();
} while (this.hasMore());
```

### Class extensions

Jatot can extend an existing Java or Jatot class without changing the original class or creating a subclass:

```java
extension String! {
    String! normalized() {
        return this.trim().toUpperCase();
    }
}
```

Usage:

```java
final normalizedName = name.normalized();
```

Class extensions are expected to lower to ordinary static Java helper methods. Real instance members take precedence over extension members.

### Virtual Thread concurrency

Ordinary Java and Jatot methods remain synchronous. Concurrency is requested at the call site:

```java
final customerFuture =
    async this.customerService.findRequired(id);

final ordersFuture =
    async this.orderService.findForCustomer(id);

final customer = await customerFuture;
final orders = await ordersFuture;
```

The intended semantics are:

```text
async expression  -> start the expression on a Virtual Thread and return a future
await future      -> wait for completion and produce the result
```

Jatot hides Virtual Thread creation, future management, exception unwrapping, cancellation, structured lifetime, and cleanup.

Immediately awaiting newly started work should be discouraged when no concurrency is gained:

```java
final customer =
    await async this.customerService.findRequired(id);
```

Prefer the ordinary synchronous call in that case:

```java
final customer =
    this.customerService.findRequired(id);
```

### Generator functions

Jatot supports generator functions to produce lazy, streamable sequences of values.
* Declare the method with the `generator` modifier.
* The method return type must be `Iterable<Type>` (or primitive/boxed types which lower to `Iterable`).
* Inside the method, use `emit <value>` to yield items to the sequence.

```java
public generator int numbers(int limit) {
    for (var i = 0; i < limit; i++) {
        emit i;
    }
}
```

Usage:
```java
for (int val : numbers(5)) {
    System.out.println(val); // prints 0, 1, 2, 3, 4
}
```

### Native Server-Side HTML Components

Jatot includes native support for server-side HTML templating via JSX-like markup tags directly integrated into the language.

#### Syntax & Rules
* **HTML Elements**: Lowercase tags (e.g. `<div class="card">`) represent standard HTML elements. Real HTML attribute names (`class`, `for`) are used instead of React-specific names (`className`, `htmlFor`).
* **Jatot Components**: Capitalized tags (e.g. `<UserCard user={user} />`) represent custom component classes or records that implement the `io.jatot.html.Component` interface.
* **Property Injection**: Component properties are matched to constructor parameters at compile-time. Property types, names, and presence of required attributes are checked at compile-time.
* **Control Flow**: You can write conditionals (`{if (cond) { ... } else { ... }}`) and loops (`{for (var item : list) { ... }}`) directly inside the markup block to control structure dynamically.
* **Fragments**: Use empty tags (`<> ... </>`) to group multiple elements without adding wrapping nodes to the DOM.
* **Security & Escaping**: All dynamic text interpolations and standard attribute values are automatically HTML-escaped to prevent XSS. Dynamic URL attributes (like `href` or `src`) are automatically validated at runtime to filter out unsafe protocols (such as `javascript:`). Raw unescaped HTML can be injected using the `TrustedHtml` wrapper class.

#### Example Component:
```java
package io.jatot.html.demo;

import io.jatot.html.Component;
import io.jatot.html.Html;

public record UserCard(User user) implements Component {
    @Override
    public Html render() {
        return (
            <article class="user-card">
                <h2>{this.user.name()}</h2>
                <p>{this.user.email()}</p>
            </article>
        );
    }
}
```

### Annotations

Jatot fully supports standard Java and framework annotations (such as Spring Boot's `@RestController`, `@Autowired`, etc.) on classes, fields, methods, constructors, and method parameters:

```java
@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private ProductService! productService;

    @GetMapping("/{id}")
    public Product! getProduct(@PathVariable String! id) {
        return this.productService.findById(id);
    }
}
```

### Named Arguments & Parameter Default Values

Method and constructor parameters can define default values, which can then be invoked optionally or using named arguments.

#### Parameter Default Values:
```java
public int calculate(int base, int multiplier = 2, int offset = 1) {
    return base * multiplier + offset;
}
```

#### Named Arguments:
```java
// 1. Relying on default parameters
final res1 = calculate(10); // 10 * 2 + 1 = 21

// 2. Named arguments out-of-order
final res2 = calculate(offset: 5, base: 10); // 10 * 2 + 5 = 25

// 3. Mixed positional and named arguments
final res3 = calculate(10, multiplier: 3); // 10 * 3 + 1 = 31
```

## Java interoperability

Java interoperability is a first-class requirement.

The target guarantees are:

1. Java APIs are directly callable from Jatot.
2. Public Jatot classes compile to ordinary JVM classes.
3. Java code can instantiate, call, extend, annotate, and reflect on Jatot classes.
4. Jatot annotations and generic signatures are preserved.
5. Jatot nullability is retained as metadata while remaining compatible with JVM descriptors.
6. Class extensions lower to normal static Java methods.
7. `async` can wrap ordinary Java or Jatot expressions.
8. Jatot requires no modified JVM.
9. Mixed Java and Jatot projects produce normal JAR files.
10. Frameworks should not need to know whether a class originated from Java or Jatot.

## Requirements

- JDK 21
- Gradle 9.6.1 or a compatible Gradle installation

The build uses a Java 21 toolchain and compiles with `--release 21`.

## Using Jatot in Your Project

Jatot is published to **GitHub Packages**. To use it as a dependency in a Gradle project (e.g. a Spring Boot project):

### 1. Add credentials

You need a GitHub Personal Access Token with at least `read:packages` scope.
Export it as environment variables (or store it in `~/.gradle/gradle.properties`):

```bash
export GITHUB_ACTOR="your_github_username"
export GITHUB_TOKEN="your_personal_access_token"
```

Or in `~/.gradle/gradle.properties`:
```properties
githubActor=your_github_username
githubToken=your_personal_access_token
```

### 2. Configure `build.gradle`

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/lemadane/jatot-lang")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("githubActor")
            password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("githubToken")
        }
    }
}

dependencies {
    // Core Jatot Compiler
    implementation 'io.jatot:jatot-compiler:0.1.0-alpha.1'
    // HTML Components Runtime
    implementation 'io.jatot:jatot-html-runtime:0.1.0-alpha.1'
    // Spring Boot Auto-configuration & Return Value Handler
    implementation 'io.jatot:jatot-html-spring:0.1.0-alpha.1'
}

sourceSets {
    main {
        java {
            srcDirs += ['build/generated/sources/jatot/main']
        }
    }
}
```

### 3. Configure the Jatot compile task

Add the Jatot transpiler task to your `build.gradle` so `.jatot` files are automatically compiled to Java during the build:

```groovy
tasks.register('compileJatot', JavaExec) {
    group = 'build'
    description = 'Compiles main Jatot source files to Java.'
    classpath = configurations.compileClasspath
    mainClass = 'io.jatot.cli.JatotCli'
    workingDir = projectDir
    
    doFirst {
        new File(projectDir, 'build/generated/sources/jatot/main').mkdirs()
    }
    
    args 'compile', 'src/main/jatot', '-d', 'build/classes/java/main', '-cp', configurations.compileClasspath.asPath, '--save-java', 'build/generated/sources/jatot/main'
    
    inputs.dir('src/main/jatot').optional()
    outputs.dir('build/generated/sources/jatot/main')
}

compileJava.dependsOn compileJatot
```

### 4. Create and Use HTML Components in Spring Boot

Place your `.jatot` files in `src/main/jatot/`.

**src/main/jatot/UserCard.jatot**:
```jatot
package com.example.demo;

import io.jatot.html.Component;
import io.jatot.html.Html;

public record UserCard(String name, String email) implements Component {
    @Override
    public Html render() {
        return (
            <div class="card">
                <h3>{this.name}</h3>
                <p>{this.email}</p>
            </div>
        );
    }
}
```

Then return the component directly from your Spring Boot controller:
```java
package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import io.jatot.html.Component;

@Controller
public class UserController {

    @GetMapping("/user")
    public Component getUser() {
        return new UserCard("Alice", "alice@example.com");
    }
}
```

The `jatot-html-spring` library will automatically intercept the returned `Component` (or raw `Html`), render it, and stream the HTML back to the browser.

### 5. Running the Demo Web Application Locally

The project includes an end-to-end demo under the `jatot-html-demo` module. You can try it yourself by following these steps:

1. Build the project and run the demo application server:
   ```bash
   ./gradlew :jatot-html-demo:runDemo
   ```
   *(Or if you are already inside the `jatot-html-demo` directory: `../gradlew runDemo`)*

2. Once the server starts on port `8080`, test the endpoints in your browser or using `curl`:
   * **Nested HTML Component rendering:** [http://localhost:8080/users/Alice](http://localhost:8080/users/Alice)
   * **Raw HTML Fragment rendering:** [http://localhost:8080/fragment](http://localhost:8080/fragment)

## Build

Build with the included Gradle wrapper:

```bash
./gradlew clean build
```

On Windows:

```cmd
gradlew.bat clean build
```

## Run the current CLI

Check whether the example source is lexically valid:

```bash
gradle run --args="check examples/HelloJatot.jatot"
```

Print its token stream:

```bash
gradle run --args="tokens examples/HelloJatot.jatot"
```

Run the convenience verification task:

```bash
gradle checkJatot
```

Current CLI usage:

```text
jatot <check|tokens> <source.jatot>
```

At this stage, `check` performs lexical validation only.

## Project structure

```text
jatot/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── jatot-compiler/
│   ├── src/main/java/io/jatot/
│   │   ├── ast/          Abstract syntax tree model
│   │   ├── cli/          Command-line interface
│   │   ├── compiler/     Compiler pipeline entry point
│   │   ├── diagnostic/   Errors and warnings
│   │   ├── emitter/      Java source code emitter
│   │   ├── lexer/        Tokenization
│   │   ├── lowering/     AST lowering (Jatot → Java-compatible forms)
│   │   ├── parser/       Recursive-descent parser
│   │   ├── semantic/     Semantic analysis and type checking
│   │   ├── source/       Source-file abstraction
│   │   └── symbol/       Symbol table and scope management
│   └── src/test/java/io/jatot/
├── jatot-html-runtime/
│   └── src/main/java/io/jatot/html/  Core server-side HTML rendering APIs
├── jatot-html-spring/
│   └── src/main/java/io/jatot/html/spring/  Spring WebMVC integration
└── jatot-html-demo/
    ├── src/main/jatot/  Jatot components (User, UserCard, UserPage)
    └── src/test/java/io/jatot/html/demo/  End-to-end integration and Spring Boot tests
```

## Compiler pipeline

```text
.jatot source
    ↓
SourceFile          — source-file abstraction
    ↓
JatotLexer          — tokenization
    ↓
Token stream        — keywords, operators, literals, identifiers
    ↓
JatotParser         — recursive-descent parsing
    ↓
AST                 — abstract syntax tree
    ↓
SemanticAnalyzer    — symbol resolution, type checking, diagnostics
    ↓
JatotLowerer        — AST lowering (Jatot constructs → Java-compatible forms)
    ↓
JavaEmitter         — Java source emission
    ↓
javac               — standard Java compilation
    ↓
JVM bytecode
```

## Implementation roadmap

Features will be added incrementally in this order:

1. **Java-compatible compiler foundation**
   - packages and imports
   - classes, fields, constructors, and methods
   - statements and expressions
   - parser and AST
   - symbol tables and diagnostics
   - Java source emission

2. **`final` and `var` local variables**
   - inferred local types
   - immutable and mutable bindings
   - assignment validation

3. **Immutable parameters**
   - parameter symbol tracking
   - reassignment diagnostics

4. **Mandatory `this.`**
   - member resolution
   - instance and static member distinction

5. **Non-null types with `!`**
   - nullable-by-default references
   - assignment and return checking
   - method-boundary checks

6. **Null coalescing with `??`**
   - lazy right-hand evaluation
   - nullable-to-non-null type refinement

7. **Optional chaining with `?.`**
   - single evaluation of receivers
   - nullable result propagation

8. **`if` expressions and `yield`**
   - branch result analysis
   - compatible yielded types

9. **`try` expressions**
   - yielded results from `try` and `catch`
   - `finally` handling

10. **`for` expressions**
    - enhanced and traditional forms
    - collection result construction
    - `break` and `continue`

11. **`while` and `do-while` expressions**
    - collection-producing loop reuse

12. **Class extensions**
    - extension discovery and imports
    - overload resolution
    - conflict handling
    - static Java helper emission

13. **`async` and `await` using Virtual Threads**
    - future typing
    - call-site concurrency
    - exception propagation
    - cancellation
    - structured task lifetime
    - ignored-future diagnostics

14. **Gradle integration**
    - `src/main/jatot`
    - `src/test/jatot`
    - generated Java source directories
    - incremental compilation support

## First implementation milestone

The first visible Jatot feature will be local mutability declarations:

```java
public class Main {
    public static void main(String[] args) {
        final message = "Hello from Jatot";
        var count = 1;

        count++;

        System.out.println(message);
        System.out.println(count);
    }
}
```

Expected Java lowering:

```java
public class Main {
    public static void main(String[] args) {
        final var message = "Hello from Jatot";
        var count = 1;

        count++;

        System.out.println(message);
        System.out.println(count);
    }
}
```

## Guiding principle

> **Jatot should remove Java's accidental complexity without hiding the programmer's intention.**
