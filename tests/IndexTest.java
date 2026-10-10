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
                @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
                @interface Mark {}
                class Base<T> {}
                class Child extends @Mark Base<String> implements java.io.Serializable {}
                interface Copy extends Runnable, List<String> {}
                class LocalOwner {
                    void first() { class Local extends Base<String> {} }
                    void second() { class Local extends Base<String> {} }
                }
                class Parent { static class Nested {} }
                class Middle extends Parent {}
                class Container extends Middle { static class Member extends Nested {} }
                """);
            var extra = new InheritanceIndex();
            extra.add(JavaSourceParser.parse(valid));
            var resolved = extra.resolve(warnings::add);
            check(resolved.get("test.Base").size() == 3, "Потеряны аннотированный или локальные классы");
            check(resolved.get("java.io.Serializable").equals(Set.of("test.Child")), "Полное имя родителя");
            check(resolved.get("java.lang.Runnable").equals(Set.of("test.Copy")), "Родитель из java.lang");
            check(resolved.get("java.util.List").equals(Set.of("test.Copy")), "Импорт с wildcard");
            check(resolved.get("test.Parent.Nested").equals(Set.of("test.Container.Member")), "Унаследованный вложенный тип");
            check(warnings.isEmpty(), "Неожиданное неразрешённое имя");

            Files.writeString(valid, "package test; import external.Base; class Imported extends Base {}");
            var external = new InheritanceIndex();
            external.add(JavaSourceParser.parse(valid));
            check(external.resolve(warnings::add).get("external.Base").equals(Set.of("test.Imported")),
                "Явный импорт должен работать без сборки зависимости");

            Files.writeString(valid, "package test; class Child extends Missing {}");
            var unknown = new InheritanceIndex();
            unknown.add(JavaSourceParser.parse(valid));
            check(unknown.resolve(warnings::add).get("[unresolved test.Child::Missing]").equals(Set.of("test.Child")),
                "Неизвестная ссылка должна остаться в индексе с явной отметкой");
            check(warnings.size() == 1, "Неизвестная ссылка должна вызвать предупреждение");
            warnings.clear();

            Path firstBase = Files.createDirectories(temporary.resolve("a")).resolve("Base.java");
            Path secondBase = Files.createDirectories(temporary.resolve("b")).resolve("Base.java");
            Files.writeString(firstBase, "package a; public class Base {}");
            Files.writeString(secondBase, "package b; public class Base {}");
            Files.writeString(valid, "package test; import a.*; import b.*; class Child extends Base {}");
            var ambiguous = new InheritanceIndex();
            ambiguous.add(JavaSourceParser.parse(firstBase));
            ambiguous.add(JavaSourceParser.parse(secondBase));
            ambiguous.add(JavaSourceParser.parse(valid));
            check(ambiguous.resolve(warnings::add).get("[unresolved test.Child::Base]").equals(Set.of("test.Child")),
                "Неоднозначные импорты не должны создавать ложную связь");
            check(warnings.size() == 1 && warnings.getFirst().contains("a.Base") && warnings.getFirst().contains("b.Base"),
                "Диагностика должна показывать оба кандидата");

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
