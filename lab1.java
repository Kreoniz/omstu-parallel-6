import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordCounter {

    public static void main(String[] args) {
        try {
            String text = Files.readString(Path.of("input.txt"));

            Pattern pattern = Pattern.compile("\\p{L}+");
            Matcher matcher = pattern.matcher(text);

            Map<String, Integer> counts = new TreeMap<>();

            while (matcher.find()) {
                String word = matcher.group().toLowerCase();

                counts.put(
                    word,
                    counts.getOrDefault(word, 0) + 1
                );
            }

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                System.out.println(
                    entry.getKey() + ": " + entry.getValue()
                );
            }

        } catch (IOException e) {
            System.err.println(
                "Ошибка чтения файла: " + e.getMessage()
            );
        }
    }
}
