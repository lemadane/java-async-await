package io.jatot.compiler;

import static org.junit.jupiter.api.Assertions.*;
import io.jatot.diagnostic.Diagnostic;
import io.jatot.diagnostic.DiagnosticSeverity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JatotEndToEndTest {

    private Path createTempDir() throws Exception {
        Path temp = Files.createTempDirectory("jatot-e2e-temp");
        temp.toFile().deleteOnExit();
        return temp;
    }

    private CompilationResult compile(Path srcDir, Path binDir, Path genDir) {
        JatotCompiler compiler = new JatotCompiler();
        List<String> cp = List.of(
            "build/classes/java/main",
            "build/classes/java/test"
        );
        return compiler.compile(List.of(srcDir), binDir, cp, genDir, true);
    }

    private void runClass(Path binDir, String mainClassName) throws Exception {
        java.net.URLClassLoader classLoader = new java.net.URLClassLoader(
            new java.net.URL[] {
                binDir.toUri().toURL(),
                Path.of("build/classes/java/main").toUri().toURL()
            },
            ClassLoader.getSystemClassLoader()
        );
        try {
            Class<?> clazz = classLoader.loadClass(mainClassName);
            java.lang.reflect.Method mainMethod = clazz.getMethod("main", String[].class);
            mainMethod.setAccessible(true);
            mainMethod.invoke(null, (Object) new String[0]);
        } finally {
            classLoader.close();
        }
    }

    @Test
    void testBlockExpressionsAndYield() throws Exception {
        String code = 
            "package test;\n" +
            "public class BlockExprMain {\n" +
            "    public static void main(String[] args) {\n" +
            "        final condition = true;\n" +
            "        final val = if (condition) {\n" +
            "            yield 100;\n" +
            "        } else {\n" +
            "            yield 200;\n" +
            "        };\n" +
            "        if (val != 100) { throw new RuntimeException(\"Value should be 100\"); }\n" +
            "\n" +
            "        var sum = 0;\n" +
            "        final list = for (var i = 0; i < 5; i++) {\n" +
            "            yield i;\n" +
            "        };\n" +
            "        for (var n : list) { sum = sum + n; }\n" +
            "        if (sum != 10) { throw new RuntimeException(\"Sum should be 10\"); }\n" +
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("BlockExprMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertTrue(result.successful(), "Compilation failed: " + result.diagnostics());

        runClass(binDir, "test.BlockExprMain");
    }

    @Test
    void testImmutableLocalsAndReassignment() throws Exception {
        String code = 
            "package test;\n" +
            "public class ImmutableMain {\n" +
            "    public static void main(String[] args) {\n" +
            "        final x = 10;\n" +
            "        x = 20;\n" + // Reassignment of final
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("ImmutableMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertFalse(result.successful(), "Expected compilation failure for reassignment of final.");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR));
    }

    @Test
    void testParameterReassignment() throws Exception {
        String code = 
            "package test;\n" +
            "public class ParamMain {\n" +
            "    public void run(int val) {\n" +
            "        val = 20;\n" + // Reassignment of parameter
            "    }\n" +
            "    public static void main(String[] args) {}\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("ParamMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertFalse(result.successful(), "Expected compilation failure for reassignment of parameter.");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR));
    }

    @Test
    void testMandatoryThisCheck() throws Exception {
        String code = 
            "package test;\n" +
            "public class ThisCheck {\n" +
            "    private int count = 0;\n" +
            "    public void inc() {\n" +
            "        count = count + 1;\n" + // Missing this.
            "    }\n" +
            "    public static void main(String[] args) {}\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("ThisCheck.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertFalse(result.successful(), "Expected compilation failure for missing this. qualification.");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR));
    }

    @Test
    void testNullCoalescingAndChaining() throws Exception {
        String code = 
            "package test;\n" +
            "public class NullMain {\n" +
            "    public static void main(String[] args) {\n" +
            "        final String name = null;\n" +
            "        final fallback = name ?? \"Default\";\n" +
            "        if (!fallback.equals(\"Default\")) { throw new RuntimeException(\"Null coalescing failed\"); }\n" +
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("NullMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertTrue(result.successful(), "Compilation failed: " + result.diagnostics());

        runClass(binDir, "test.NullMain");
    }

    @Test
    void testClassExtensions() throws Exception {
        String code = 
            "package test;\n" +
            "extension String! {\n" +
            "    public String! shout() {\n" +
            "        return this.toUpperCase();\n" +
            "    }\n" +
            "}\n" +
            "class ExtMain {\n" +
            "    public static void main(String[] args) {\n" +
            "        final text = \"hello\";\n" +
            "        final result = text.shout();\n" +
            "        if (!result.equals(\"HELLO\")) { throw new RuntimeException(\"Extension method failed\"); }\n" +
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("ExtMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertTrue(result.successful(), "Compilation failed: " + result.diagnostics());

        runClass(binDir, "test.ExtMain");
    }

    @Test
    void testAsyncAwaitConcurrency() throws Exception {
        String code = 
            "package test;\n" +
            "public class AsyncMain {\n" +
            "    public static void main(String[] args) {\n" +
            "        final fut1 = async 42;\n" +
            "        final val = await fut1;\n" +
            "        if (val != 42) { throw new RuntimeException(\"Async/await failed\"); }\n" +
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("AsyncMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertTrue(result.successful(), "Compilation failed: " + result.diagnostics());

        runClass(binDir, "test.AsyncMain");
    }

    @Test
    void testAnnotationPreservation() throws Exception {
        String code = 
            "package test;\n" +
            "@Deprecated\n" +
            "public class AnnotationMain {\n" +
            "    @SuppressWarnings(\"unchecked\")\n" +
            "    public static void main(String[] args) {\n" +
            "        final val = 100;\n" +
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("AnnotationMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertTrue(result.successful(), "Compilation failed: " + result.diagnostics());

        runClass(binDir, "test.AnnotationMain");

        String genJava = Files.readString(genDir.resolve("test/AnnotationMain.java"), StandardCharsets.UTF_8);
        assertTrue(genJava.contains("@Deprecated"), "Generated Java missing @Deprecated class annotation");
        assertTrue(genJava.contains("@SuppressWarnings(\"unchecked\")"), "Generated Java missing @SuppressWarnings member annotation");
    }

    @Test
    void testDefaultParamsAndNamedArgs() throws Exception {
        String code = 
            "package test;\n" +
            "public class DefaultParamsMain {\n" +
            "    public static int calculate(int base, int multiplier = 2, int offset = 1) {\n" +
            "        return base * multiplier + offset;\n" +
            "    }\n" +
            "    public static void main(String[] args) {\n" +
            "        // 1. Positional only with defaults\n" +
            "        final res1 = calculate(10);\n" +
            "        if (res1 != 21) { throw new RuntimeException(\"Test 1 failed: \" + res1); }\n" +
            "\n" +
            "        // 2. Named arguments out of order\n" +
            "        final res2 = calculate(offset: 5, base: 10);\n" +
            "        if (res2 != 25) { throw new RuntimeException(\"Test 2 failed: \" + res2); }\n" +
            "\n" +
            "        // 3. Mixed positional and named arguments\n" +
            "        final res3 = calculate(10, multiplier: 3);\n" +
            "        if (res3 != 31) { throw new RuntimeException(\"Test 3 failed: \" + res3); }\n" +
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("DefaultParamsMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertTrue(result.successful(), "Compilation failed: " + result.diagnostics());

        runClass(binDir, "test.DefaultParamsMain");
    }

    @Test
    void testTernaryExpression() throws Exception {
        String code = 
            "package test;\n" +
            "public class TernaryMain {\n" +
            "    public static void main(String[] args) {\n" +
            "        final condition = true;\n" +
            "        final val = condition ? \"Zack\" : \"Guest\";\n" +
            "        if (!val.equals(\"Zack\")) { throw new RuntimeException(\"Expected Zack\"); }\n" +
            "\n" +
            "        final nested = condition ? (false ? 1 : 2) : 3;\n" +
            "        if (nested != 2) { throw new RuntimeException(\"Expected 2\"); }\n" +
            "    }\n" +
            "}\n";

        Path tempDir = createTempDir();
        Path srcDir = tempDir.resolve("src");
        Path binDir = tempDir.resolve("bin");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("TernaryMain.jatot"), code, StandardCharsets.UTF_8);

        CompilationResult result = compile(srcDir, binDir, genDir);
        assertTrue(result.successful(), "Compilation failed: " + result.diagnostics());

        runClass(binDir, "test.TernaryMain");
    }
}
