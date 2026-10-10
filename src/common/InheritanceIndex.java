package common;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class InheritanceIndex {
    private final Set<String> declarations = new HashSet<>();
    private final Map<ParsedSource.Parent, Set<String>> children = new HashMap<>();

    public void add(ParsedSource source) {
        declarations.addAll(source.declarations());
        source.children().forEach((parent, names) -> {
            Set<String> merged = children.getOrDefault(parent, new HashSet<>());
            merged.addAll(names);
            children.put(parent, merged);
        });
    }

    // Разрешение имён происходит после чтения всех файлов: порядок потоков не влияет на результат.
    public Map<String, Set<String>> resolve(Consumer<String> warning) {
        Map<String, Set<String>> result = new TreeMap<>();
        declarations.forEach(name -> result.put(name, new TreeSet<>()));
        children.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                String parent = resolveName(entry.getKey(), warning);
                Set<String> names = result.getOrDefault(parent, new TreeSet<>());
                names.addAll(entry.getValue());
                result.put(parent, names);
            });
        return result;
    }

    private String resolveName(ParsedSource.Parent parent, Consumer<String> warning) {
        String name = parent.name();
        for (String scope : parent.scopes()) {
            if (declarations.contains(scope + "." + name)) {
                return scope + "." + name;
            }
        }
        String head = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        List<String> explicit = parent.imports().stream()
            .filter(item -> !item.endsWith(".*"))
            .filter(item -> item.substring(item.lastIndexOf('.') + 1).equals(head))
            .map(item -> item + name.substring(head.length())).distinct().toList();
        if (explicit.size() == 1) {
            return explicit.getFirst();
        }
        if (explicit.size() > 1) {
            return unresolved(parent, warning, explicit);
        }
        String inPackage = parent.packageName().isEmpty() ? name : parent.packageName() + "." + name;
        if (declarations.contains(inPackage)) {
            return inPackage;
        }
        if (declarations.contains(name)) {
            return name;
        }
        Set<String> candidates = new TreeSet<>();
        List<String> wildcards = parent.imports().stream().filter(item -> item.endsWith(".*"))
            .map(item -> item.substring(0, item.length() - 1)).toList();
        for (String prefix : wildcards) {
            String candidate = prefix + name;
            if (declarations.contains(candidate) || isPlatformType(candidate)) {
                candidates.add(candidate);
            }
        }
        if (isPlatformType("java.lang." + name)) {
            candidates.add("java.lang." + name);
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        if (candidates.size() > 1) {
            return unresolved(parent, warning, candidates);
        }
        // Явное полное имя можно сохранить даже без установленной внешней зависимости.
        if (name.contains(".") && Character.isLowerCase(name.charAt(0))) {
            return name;
        }
        return unresolved(parent, warning, candidates);
    }

    private static boolean isPlatformType(String name) {
        String binaryName = name;
        while (true) {
            try {
                Class.forName(binaryName, false, ClassLoader.getPlatformClassLoader());
                return true;
            } catch (ClassNotFoundException exception) {
                int dot = binaryName.lastIndexOf('.');
                if (dot < 0) {
                    return false;
                }
                binaryName = binaryName.substring(0, dot) + "$" + binaryName.substring(dot + 1);
            } catch (LinkageError exception) {
                return false;
            }
        }
    }

    private static String unresolved(ParsedSource.Parent parent, Consumer<String> warning, Collection<String> candidates) {
        String context = parent.scopes().getFirst();
        warning.accept("Не удалось однозначно разрешить " + parent.name() + " в " + context
            + (candidates.isEmpty() ? "" : "; кандидаты: " + candidates));
        return "[unresolved " + context + "::" + parent.name() + "]";
    }

    public void print() {
        resolve(message -> System.err.println(message)).forEach((parent, names) ->
            System.out.println(parent + " -> " + names.stream().sorted().collect(Collectors.joining(", "))));
    }
}
