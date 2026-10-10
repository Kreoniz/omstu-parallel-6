package lab1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordCounter {
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Использование: lab1.WordCounter [текстовый файл]");
        }
        Path input = Path.of(args.length == 0 ? "input.txt" : args[0]);
        String text = Files.readString(input);
        Pattern pattern = Pattern.compile("\\p{L}[\\p{L}\\p{M}]*");
        Matcher matcher = pattern.matcher(text);
        Map<String, Integer> counts = new TreeMap<>();

        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);
            counts.put(word, counts.getOrDefault(word, 0) + 1);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }
}
