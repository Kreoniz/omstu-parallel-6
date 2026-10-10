package common;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ParsedSource(Set<String> declarations, Map<Parent, Set<String>> children) {
    public record Parent(String name, String packageName, List<String> scopes, List<String> imports) {}
}
