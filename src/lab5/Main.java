package lab5;

import common.InheritanceIndex;
import common.JavaSourceParser;
import common.ParsedSource;
import common.SourceFiles;
import common.WorkerCount;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException("Использование: lab5.Main [папка Java-проекта] [число потоков]");
        }
        int workers = WorkerCount.fromArguments(args);
        var files = SourceFiles.find(Path.of(args.length == 0 ? "examples/project" : args[0]));
        var index = new InheritanceIndex();
        List<Future<ParsedSource>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            for (Path file : files) {
                futures.add(executor.submit(() -> JavaSourceParser.parse(file)));
            }
            // Все задания уже выданы. Только теперь начинаем получать результаты.
            try {
                for (Future<ParsedSource> future : futures) {
                    index.add(future.get());
                }
            } catch (Exception exception) {
                futures.forEach(future -> future.cancel(true));
                throw exception;
            }
        }
        index.print();
    }
}
