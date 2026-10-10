import common.InheritanceIndex;
import common.JavaSourceParser;
import common.SourceFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class IndexTest {
    public static void main(String[] args) throws Exception {
        var index = new InheritanceIndex();
        for (var file : SourceFiles.find(Path.of("examples/project"))) {
            index.add(JavaSourceParser.parse(file));
        }
        Map<String, Set<String>> expected = Map.of(
            "demo.app.Child", Set.of(),
            "demo.app.Document", Set.of(),
            "demo.app.Document.Nested", Set.of(),
            "demo.app.Outer", Set.of(),
            "demo.app.Outer.Inner", Set.of("demo.app.Child"),
            "demo.base.Base", Set.of("demo.app.Document"),
            "demo.base.Readable", Set.of("demo.base.Writable"),
            "demo.base.Writable", Set.of("demo.app.Document"),
            "demo.other.Base", Set.of("demo.app.Document.Nested")
        );
        var warnings = new ArrayList<String>();
        check(index.resolve(warnings::add).equals(expected), "Индекс примера не совпал с ожидаемым");
        check(warnings.isEmpty(), "В примере не должно быть неразрешённых имён");

        Path temporary = Files.createTempDirectory(Path.of("build"), "index-test-");
        try {
            Path valid = temporary.resolve("Types.java");
            Files.writeString(valid, """
                package test;
                import java.util.*;
                @interface Mark {}
                class Base<T> {}
                class Child extends @Mark Base<String> implements java.io.Serializable {}
                interface Copy extends Runnable, List<String> {}
                class LocalOwner {
                    void first() { class Local extends Base<String> {} }
                    void second() { class Local extends Base<String> {} }
                }
                """);
            var extra = new InheritanceIndex();
            extra.add(JavaSourceParser.parse(valid));
            var resolved = extra.resolve(warnings::add);
            check(resolved.get("test.Base").size() == 3, "Потеряны аннотированный или локальные классы");
            check(resolved.get("java.io.Serializable").equals(Set.of("test.Child")), "Полное имя родителя");
            check(resolved.get("java.lang.Runnable").equals(Set.of("test.Copy")), "Родитель из java.lang");
            check(resolved.get("java.util.List").equals(Set.of("test.Copy")), "Импорт с wildcard");
            check(warnings.isEmpty(), "Неожиданное неразрешённое имя");

            Files.writeString(valid, "class Broken extends {");
            try {
                JavaSourceParser.parse(valid);
                throw new AssertionError("Некорректный Java-файл не вызвал ошибку");
            } catch (IOException expectedFailure) {
                check(expectedFailure.getMessage().contains("Types.java"), "Ошибка должна содержать имя файла");
            }
            try {
                SourceFiles.find(temporary.resolve("missing"));
                throw new AssertionError("Отсутствующая папка не вызвала ошибку");
            } catch (IOException expectedFailure) {
                check(expectedFailure.getMessage().contains("missing"), "Ошибка должна содержать путь");
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        System.out.println("IndexTest: OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
