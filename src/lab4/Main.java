package lab4;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Использование: lab4.Main [папка Java-проекта]");
        }
        Path root = Path.of(args.length == 0 ? "examples/project" : args[0]);
        List<Path> files = findJavaFiles(root);
        Map<String, Set<String>> index = new TreeMap<>();
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch completed = new CountDownLatch(files.size());
        Exception[] failures = new Exception[files.size()];

        for (int i = 0; i < files.size(); i++) {
            int position = i;
            Path file = files.get(i);
            Thread.ofVirtual().name("lab4-" + i).start(() -> {
                try {
                    Map<String, Set<String>> localIndex = buildIndex(file);
                    lock.lock();
                    try {
                        mergeIndex(index, localIndex);
                    } finally {
                        lock.unlock();
                    }
                } catch (Exception failure) {
                    failures[position] = failure;
                } finally {
                    completed.countDown();
                }
            });
        }
        completed.await();

        for (int i = 0; i < failures.length; i++) {
            if (failures[i] != null) {
                throw new IOException("Не удалось обработать файл: " + files.get(i), failures[i]);
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
