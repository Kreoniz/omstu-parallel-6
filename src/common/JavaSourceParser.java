package common;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import javax.tools.*;

public final class JavaSourceParser {
    private JavaSourceParser() {}

    public static ParsedSource parse(Path file) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("Нужен JDK с компилятором Java. Запустите программу через mise exec.");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        Set<String> declarations = new HashSet<>();
        Map<ParsedSource.Parent, Set<String>> children = new HashMap<>();

        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var source = manager.getJavaFileObjects(file.toFile());
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, source);
            var positions = Trees.instance(task).getSourcePositions();
            var units = task.parse();
            checkDiagnostics(diagnostics, file);
            for (var unit : units) {
                String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                List<String> imports = unit.getImports().stream()
                    .map(item -> item.getQualifiedIdentifier().toString()).toList();

                new TreePathScanner<Void, Void>() {
                    private final Deque<String> scopes = new ArrayDeque<>();

                    @Override
                    public Void visitClass(ClassTree type, Void unused) {
                        String simpleName = type.getSimpleName().toString();
                        if (simpleName.isEmpty()) {
                            String scope = (scopes.isEmpty() ? packageName : scopes.peek())
                                + ".$anonymous" + positions.getStartPosition(type);
                            scopes.push(scope);
                            try {
                                return super.visitClass(type, unused);
                            } finally {
                                scopes.pop();
                            }
                        }
                        Tree owner = getCurrentPath().getParentPath().getLeaf();
                        if (!(owner instanceof CompilationUnitTree) && !(owner instanceof ClassTree)) {
                            simpleName += "$local" + positions.getStartPosition(type);
                        }
                        String prefix = scopes.isEmpty() ? packageName : scopes.peek();
                        String child = prefix.isEmpty() ? simpleName : prefix + "." + simpleName;
                        declarations.add(child);
                        scopes.push(child);
                        try {
                            List<Tree> parents = new ArrayList<>();
                            if (type.getExtendsClause() != null) {
                                parents.add(type.getExtendsClause());
                            }
                            parents.addAll(type.getImplementsClause());
                            for (Tree parentType : parents) {
                                var parent = new ParsedSource.Parent(typeName(parentType), packageName,
                                    List.copyOf(scopes), imports);
                                Set<String> names = children.getOrDefault(parent, new HashSet<>());
                                names.add(child);
                                children.put(parent, names);
                            }
                            return super.visitClass(type, unused);
                        } finally {
                            scopes.pop();
                        }
                    }
                }.scan(unit, null);
            }
        }
        return new ParsedSource(declarations, children);
    }

    private static void checkDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics, Path file) throws IOException {
        String errors = diagnostics.getDiagnostics().stream()
            .filter(item -> item.getKind() == Diagnostic.Kind.ERROR)
            .map(item -> file + ":" + item.getLineNumber() + ": " + item.getMessage(Locale.ROOT))
            .collect(Collectors.joining(System.lineSeparator()));
        if (!errors.isEmpty()) {
            throw new IOException("Ошибка разбора Java:\n" + errors);
        }
    }

    private static String typeName(Tree type) {
        return switch (type) {
            case ParameterizedTypeTree item -> typeName(item.getType());
            case AnnotatedTypeTree item -> typeName(item.getUnderlyingType());
            case MemberSelectTree item -> typeName(item.getExpression()) + "." + item.getIdentifier();
            case IdentifierTree item -> item.getName().toString();
            default -> throw new IllegalArgumentException("Неожиданный тип родителя: " + type.getKind());
        };
    }
}
