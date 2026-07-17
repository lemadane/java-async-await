import java.nio.file.*;
import java.util.*;

public class scratch {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("jatot-compiler/src/main/java/io/jatot/parser/JatotParser.java"));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("parseParameter")) {
                for (int j = i; j < i + 15 && j < lines.size(); j++) {
                    System.out.println(lines.get(j));
                }
                break;
            }
        }
    }
}
