package common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SourceFiles {
    private SourceFiles() {}

    public static List<Path> find(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Нет папки проекта: " + root);
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted().toList();
        }
    }
}
