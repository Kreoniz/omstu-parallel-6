import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IntegrationTest {
    private record Run(int exit, String output, String error) {}
    private static Path temporary;
    private static int checks;

    public static void main(String[] args) throws Exception {
        temporary = Files.createTempDirectory(Path.of("build"), "integration-");
        try {
            Path example = Path.of("examples/project");
            String expected = successful(run("lab2.Main", example.toString()));
            for (int lab = 3; lab <= 6; lab++) {
                check(successful(run("lab" + lab + ".Main", example.toString())).equals(expected),
                    "Отличается индекс лабораторной " + lab);
            }
            for (int workers : List.of(1, 3, 12)) {
                for (int lab : List.of(5, 6)) {
                    check(successful(run("lab" + lab + ".Main", example.toString(), "" + workers)).equals(expected),
                        "Отличается индекс при числе потоков " + workers);
                }
            }

            Path large = Files.createDirectory(temporary.resolve("large"));
            Files.writeString(large.resolve("Root.java"), "package bench; class Root {}");
            for (int i = 0; i < 128; i++) {
                Files.writeString(large.resolve("Node" + i + ".java"),
                    "package bench; class Node" + i + " extends Root implements Runnable { public void run() {} }");
            }
            expected = successful(run("lab2.Main", large.toString()));
            check(expected.lines().count() == 130, "Потеряны типы в большом индексе");
            for (int lab = 3; lab <= 6; lab++) {
                Run result = lab >= 5 ? run("lab" + lab + ".Main", large.toString(), "2")
                    : run("lab" + lab + ".Main", large.toString());
                check(successful(result).equals(expected), "Потеряны результаты при параллельной обработке " + lab);
            }

            Path empty = Files.createDirectory(temporary.resolve("empty"));
            Path broken = Files.createDirectory(temporary.resolve("broken"));
            Files.writeString(broken.resolve("Good.java"), "class Good {}");
            Files.writeString(broken.resolve("Bad.java"), "class Bad extends {");
            for (int lab = 2; lab <= 6; lab++) {
                String main = "lab" + lab + ".Main";
                check(successful(run(main, empty.toString())).isEmpty(), "Непустой индекс пустой папки");
                Run failure = run(main, broken.toString());
                check(failure.exit() != 0 && failure.error().contains("Bad.java"), "Потеряна ошибка разбора в " + main);
                check(failure.output().isEmpty(), "Напечатан частичный индекс при ошибке в " + main);
                failure = run(main, temporary.resolve("missing").toString());
                check(failure.exit() != 0 && failure.error().contains("missing"), "Потеряна ошибка пути в " + main);
            }
            for (int lab : List.of(5, 6)) {
                for (String invalid : List.of("0", "-1", "abc")) {
                    check(run("lab" + lab + ".Main", example.toString(), invalid).exit() != 0,
                        "Принято неправильное число потоков");
                }
            }

            Path words = temporary.resolve("words.txt");
            Files.writeString(words, "Ура!!! УРА??? слово—слово; hello...HELLO\nI i\nΚαλημέρα");
            check(successful(run("lab1.WordCounter", words.toString())).equals(
                "hello: 2\ni: 2\nκαλημέρα: 1\nслово: 2\nура: 2\n"), "Регистры, Unicode или знаки препинания");
            Files.writeString(words, "...!?—,,");
            check(successful(run("lab1.WordCounter", words.toString())).isEmpty(), "Знаки препинания посчитаны словами");
            check(run("lab1.WordCounter", temporary.resolve("missing.txt").toString()).exit() != 0,
                "Отсутствующий текстовый файл не вызвал ошибку");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("IntegrationTest: OK (" + checks + " проверок)");
    }

    private static Run run(String main, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(List.of("-Xmx256m", "-XX:ActiveProcessorCount=4", "-Duser.language=tr",
            "-Duser.country=TR", "-cp", Path.of("build/classes").toAbsolutePath().toString(), main));
        command.addAll(List.of(args));
        Path output = Files.createTempFile(temporary, "stdout-", ".txt");
        Path error = Files.createTempFile(temporary, "stderr-", ".txt");
        Process process = new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                throw new AssertionError("Программа зависла: " + command);
            }
            return new Run(process.exitValue(), Files.readString(output), Files.readString(error));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static String successful(Run result) {
        check(result.exit() == 0, "Ошибка процесса: " + result.error());
        check(result.error().isEmpty(), "Неожиданная диагностика: " + result.error());
        return result.output();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
