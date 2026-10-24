package lab3;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

public class Main {
    private record FileResult(Path file, Map<String, Set<String>> index, Exception failure) {}

    public static void main(String[] args) throws Exception {
        boolean benchmark = args.length > 0 && args[0].equals("--benchmark");
        int offset = benchmark ? 1 : 0;
        if (args.length - offset > 1) {
            throw new IllegalArgumentException("Использование: lab3.Main [--benchmark] [папка Java-проекта]");
        }
        Path root = Path.of(args.length == offset ? "build/spring-framework" : args[offset]);
        List<Path> files = findJavaFiles(root);
        FileResult[] results = new FileResult[files.size()];
        CountDownLatch completed = new CountDownLatch(files.size());
        long started = System.nanoTime();

        for (int i = 0; i < files.size(); i++) {
            int position = i;
            Path file = files.get(i);
            Thread.ofVirtual().name("lab3-" + i).start(() -> {
                try {
                    results[position] = new FileResult(file, buildIndex(file), null);
                } catch (Exception failure) {
                    results[position] = new FileResult(file, Map.of(), failure);
                } finally {
                    completed.countDown();
                }
            });
        }
        completed.await();
        long elapsed = System.nanoTime() - started;

        for (FileResult result : results) {
            if (result == null || result.failure() != null) {
                throw new IOException("Не удалось обработать файл: "
                    + (result == null ? "поток завершился без результата" : result.file()),
                    result == null ? null : result.failure());
            }
        }
        if (benchmark) {
            System.out.printf(Locale.ROOT, "files=%d workers=%d index_ms=%.3f%n",
                files.size(), files.size(), elapsed / 1_000_000.0);
            return;
        }
        for (FileResult result : results) {
            System.out.println("FILE " + root.relativize(result.file()));
            for (var entry : result.index().entrySet()) {
                String line = entry.getKey() + " -> " + String.join(", ", entry.getValue());
                System.out.println(line.stripTrailing());
            }
        }
    }

    private static List<Path> findJavaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Нет папки проекта: " + root);
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .toList();
        }
    }

    private static Map<String, Set<String>> buildIndex(Path file) throws IOException {
        Map<String, Set<String>> index = new TreeMap<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();

        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var sources = manager.getJavaFileObjects(file.toFile());
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of(), null, sources);

            for (var unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitClass(ClassTree type, Void unused) {
                        boolean isClassOrInterface = type.getKind() == Tree.Kind.CLASS
                            || type.getKind() == Tree.Kind.INTERFACE;
                        String child = type.getSimpleName().toString();
                        if (!isClassOrInterface || child.isEmpty()) {
                            return super.visitClass(type, unused);
                        }
                        index.putIfAbsent(child, new TreeSet<>());

                        List<Tree> parents = new ArrayList<>();
                        if (type.getExtendsClause() != null) {
                            parents.add(type.getExtendsClause());
                        }
                        parents.addAll(type.getImplementsClause());

                        for (Tree parentType : parents) {
                            String parent = simpleTypeName(parentType);
                            Set<String> children = index.getOrDefault(parent, new TreeSet<>());
                            children.add(child);
                            index.put(parent, children);
                        }
                        return super.visitClass(type, unused);
                    }
                }.scan(unit, null);
            }
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IOException(file + ":" + diagnostic.getLineNumber() + ": "
                        + diagnostic.getMessage(null));
                }
            }
        }
        return index;
    }

    private static String simpleTypeName(Tree type) {
        if (type instanceof ParameterizedTypeTree genericType) {
            return simpleTypeName(genericType.getType());
        }
        if (type instanceof AnnotatedTypeTree annotatedType) {
            return simpleTypeName(annotatedType.getUnderlyingType());
        }
        String name = type.toString();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
