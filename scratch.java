import java.nio.file.*;
import java.util.*;

public class scratch {
    public static void main(String[] args) throws Exception {
        String code = "sql<User>\"\"\"\n{}\n\"\"\"";
        io.jatot.source.SourceFile f = new io.jatot.source.SourceFile(Paths.get("test"), code);
        io.jatot.lexer.JatotLexer lexer = new io.jatot.lexer.JatotLexer(f);
        io.jatot.lexer.LexResult res = lexer.lex();
        for (io.jatot.lexer.Token t : res.tokens()) {
            System.out.println(t.type() + " " + t.lexeme());
        }
    }
}
