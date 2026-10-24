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
import java.util.Comparator;
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

class MainOptimized {
    private record FileResult(Path file, Map<String, Set<String>> index) {}

    public static void main(String[] args) throws Exception {
        boolean benchmark = args.length > 0 && args[0].equals("--benchmark");
        int offset = benchmark ? 1 : 0;
        if (args.length - offset > 2) {
            throw new IllegalArgumentException(
                "Использование: MainOptimized [--benchmark] [папка Java-проекта] [число потоков]");
        }
        Path root = Path.of(args.length == offset ? "build/spring-framework" : args[offset])
            .toAbsolutePath().normalize();
        int workerCount = args.length - offset < 2
            ? Runtime.getRuntime().availableProcessors()
            : Integer.parseInt(args[offset + 1]);
        if (workerCount < 1) {
            throw new IllegalArgumentException("Число потоков должно быть положительным");
        }
        List<Path> files = findJavaFiles(root);
        int batchCount = Math.min(workerCount, files.size());
        FileResult[][] results = new FileResult[batchCount][];
        Exception[] failures = new Exception[batchCount];
        CountDownLatch completed = new CountDownLatch(batchCount);
        long started = System.nanoTime();

        for (int i = 0; i < batchCount; i++) {
            int position = i;
            int from = i * files.size() / batchCount;
            int to = (i + 1) * files.size() / batchCount;
            List<Path> batch = files.subList(from, to);
            Thread.ofPlatform().name("lab3-optimized-" + i).start(() -> {
                try {
                    results[position] = buildIndexes(batch);
                } catch (Exception failure) {
                    failures[position] = failure;
                } finally {
                    completed.countDown();
                }
            });
        }
        completed.await();
        long elapsed = System.nanoTime() - started;

        for (int i = 0; i < batchCount; i++) {
            if (failures[i] != null || results[i] == null) {
                throw new IOException("Не удалось обработать пакет " + i, failures[i]);
            }
        }
        if (benchmark) {
            System.out.printf(Locale.ROOT, "files=%d workers=%d index_ms=%.3f%n",
                files.size(), batchCount, elapsed / 1_000_000.0);
            return;
        }
        for (FileResult[] batch : results) {
            for (FileResult result : batch) {
                System.out.println("FILE " + root.relativize(result.file()));
                for (var entry : result.index().entrySet()) {
                    String line = entry.getKey() + " -> " + String.join(", ", entry.getValue());
                    System.out.println(line.stripTrailing());
                }
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

    private static FileResult[] buildIndexes(List<Path> files) throws IOException {
        List<FileResult> results = new ArrayList<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();

        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var sources = manager.getJavaFileObjectsFromPaths(files);
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of(), null, sources);

            for (var unit : task.parse()) {
                Map<String, Set<String>> index = new TreeMap<>();
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
                Path file = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                results.add(new FileResult(file, index));
            }
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IOException(diagnostic.getSource().getName() + ":"
                        + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null));
                }
            }
        }
        results.sort(Comparator.comparing(FileResult::file));
        return results.toArray(FileResult[]::new);
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
