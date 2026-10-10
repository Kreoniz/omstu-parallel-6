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
        Map<ParsedSource.Parent, String> names = new HashMap<>();
        // Сначала находятся обычные родители. Затем их связи помогают найти
        // унаследованные вложенные типы. Повторяем до стабилизации результата.
        for (int step = 0; step <= declarations.size(); step++) {
            Map<String, Set<String>> ancestors = ancestors(names);
            Map<ParsedSource.Parent, String> next = new HashMap<>();
            for (var parent : children.keySet()) {
                next.put(parent, resolveName(parent, message -> {}, ancestors));
            }
            if (next.equals(names)) {
                names = next;
                break;
            }
            names = next;
        }
        Map<String, Set<String>> ancestors = ancestors(names);
        Map<String, Set<String>> result = new TreeMap<>();
        declarations.forEach(name -> result.put(name, new TreeSet<>()));
        children.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                String parent = resolveName(entry.getKey(), warning, ancestors);
                if (parent == null) {
                    parent = "[unresolved " + entry.getKey().scopes().getFirst() + "::" + entry.getKey().name() + "]";
                }
                Set<String> subclasses = result.getOrDefault(parent, new TreeSet<>());
                subclasses.addAll(entry.getValue());
                result.put(parent, subclasses);
            });
        return result;
    }

    private Map<String, Set<String>> ancestors(Map<ParsedSource.Parent, String> names) {
        Map<String, Set<String>> result = new HashMap<>();
        children.forEach((reference, subclasses) -> {
            String parent = names.get(reference);
            if (parent != null) {
                for (String subclass : subclasses) {
                    result.computeIfAbsent(subclass, key -> new HashSet<>()).add(parent);
                }
            }
        });
        return result;
    }

    private void inheritedTypes(String scope, String name, Map<String, Set<String>> ancestors,
                                Set<String> visited, Set<String> matches) {
        if (!visited.add(scope)) {
            return;
        }
        for (String parent : ancestors.getOrDefault(scope, Set.of())) {
            String candidate = parent + "." + name;
            if (declarations.contains(candidate)) {
                matches.add(candidate);
            } else {
                inheritedTypes(parent, name, ancestors, visited, matches);
            }
        }
    }

    private String resolveName(ParsedSource.Parent parent, Consumer<String> warning,
                               Map<String, Set<String>> ancestors) {
        String name = parent.name();
        for (String scope : parent.scopes()) {
            if (declarations.contains(scope + "." + name)) {
                return scope + "." + name;
            }
            Set<String> inherited = new TreeSet<>();
            inheritedTypes(scope, name, ancestors, new HashSet<>(), inherited);
            if (inherited.size() == 1) {
                return inherited.iterator().next();
            }
            if (inherited.size() > 1) {
                return unresolved(parent, warning, inherited);
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
        return null;
    }

    public void print() {
        resolve(message -> System.err.println(message)).forEach((parent, names) ->
            System.out.println(parent + " -> " + names.stream().sorted().collect(Collectors.joining(", "))));
    }
}
