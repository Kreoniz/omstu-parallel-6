package common;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Результат одного файла: объявления типов и ещё не разрешённые ссылки на родителей.
public record ParsedSource(Set<String> declarations, Map<Parent, Set<String>> children) {
    public record Parent(String name, String packageName, List<String> scopes, List<String> imports) {}
}
