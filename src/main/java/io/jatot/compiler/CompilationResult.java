package io.jatot.compiler;

import io.jatot.ast.Ast.CompilationUnit;
import io.jatot.diagnostic.Diagnostic;
import io.jatot.diagnostic.DiagnosticSeverity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CompilationResult(
        Optional<CompilationUnit> compilationUnit,
        List<Diagnostic> diagnostics) {

    public CompilationResult {
        compilationUnit = Objects.requireNonNull(compilationUnit, "compilationUnit");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public boolean successful() {
        return diagnostics.stream()
                .noneMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }
}
