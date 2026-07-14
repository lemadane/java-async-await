package io.jatot.lexer;

import java.util.Objects;

public record Token(TokenType type, String lexeme, int line, int column) {
    public Token {
        type = Objects.requireNonNull(type, "type");
        lexeme = Objects.requireNonNull(lexeme, "lexeme");
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("Token positions are one-based");
        }
    }
}
