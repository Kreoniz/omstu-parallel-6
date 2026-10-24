package lab5;

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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException("Использование: lab5.Main [папка Java-проекта] [число потоков]");
        }
        Path root = Path.of(args.length == 0 ? "examples/project" : args[0]);
        int workerCount = args.length < 2
            ? Math.min(4, Runtime.getRuntime().availableProcessors())
            : Integer.parseInt(args[1]);
        if (workerCount < 1) {
            throw new IllegalArgumentException("Число потоков должно быть положительным");
        }
        List<Path> files = findJavaFiles(root);
        Map<String, Set<String>> index = new TreeMap<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(workerCount)) {
            List<Future<Map<String, Set<String>>>> futures = new ArrayList<>();
            for (Path file : files) {
                futures.add(executor.submit(() -> buildIndex(file)));
            }
            for (Future<Map<String, Set<String>>> future : futures) {
                mergeIndex(index, future.get());
            }
        }

        for (var entry : index.entrySet()) {
            String line = entry.getKey() + " -> " + String.join(", ", entry.getValue());
            System.out.println(line.stripTrailing());
        }
    }

    private static void mergeIndex(Map<String, Set<String>> index, Map<String, Set<String>> addition) {
        for (var entry : addition.entrySet()) {
            Set<String> children = index.getOrDefault(entry.getKey(), new TreeSet<>());
            children.addAll(entry.getValue());
            index.put(entry.getKey(), children);
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
