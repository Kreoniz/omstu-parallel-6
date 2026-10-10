package lab2;

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
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Использование: lab2.Main [папка Java-проекта]");
        }
        Path root = Path.of(args.length == 0 ? "examples/project" : args[0]);
        List<Path> files = findJavaFiles(root);
        Map<String, Set<String>> index = buildIndex(files);

        for (var entry : index.entrySet()) {
            String line = entry.getKey() + " -> " + String.join(", ", entry.getValue());
            System.out.println(line.stripTrailing());
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

    private static Map<String, Set<String>> buildIndex(List<Path> files) throws IOException {
        Map<String, Set<String>> index = new TreeMap<>();
        if (files.isEmpty()) {
            return index;
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("Нужен JDK. Запустите программу через mise exec.");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();

        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var sources = manager.getJavaFileObjectsFromFiles(files.stream().map(Path::toFile).toList());
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, sources);

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
                    throw new IOException(diagnostic.getSource().getName() + ":"
                        + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(Locale.ROOT));
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
