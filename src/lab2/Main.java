package lab2;

import common.InheritanceIndex;
import common.JavaSourceParser;
import common.SourceFiles;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Использование: lab2.Main [папка Java-проекта]");
        }
        Path root = Path.of(args.length == 0 ? "examples/project" : args[0]);
        InheritanceIndex index = new InheritanceIndex();
        for (Path file : SourceFiles.find(root)) {
            index.add(JavaSourceParser.parse(file));
        }
        index.print();
    }
}
