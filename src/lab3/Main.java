package lab3;

import common.InheritanceIndex;
import common.JavaSourceParser;
import common.ParsedSource;
import common.SourceFiles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Main {
    private static class FileTask implements Runnable {
        private final Path file;
        private final CountDownLatch completion;
        private ParsedSource result;
        private Exception failure;

        FileTask(Path file, CountDownLatch completion) {
            this.file = file;
            this.completion = completion;
        }

        @Override
        public void run() {
            try {
                result = JavaSourceParser.parse(file);
            } catch (Exception exception) {
                failure = exception;
            } finally {
                completion.countDown();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Использование: lab3.Main [папка Java-проекта]");
        }
        var files = SourceFiles.find(Path.of(args.length == 0 ? "examples/project" : args[0]));
        var completion = new CountDownLatch(files.size());
        List<FileTask> tasks = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (Path file : files) {
            var task = new FileTask(file, completion);
            tasks.add(task);
            threads.add(Thread.ofVirtual().name("parse-" + file.getFileName()).start(task));
        }
        try {
            completion.await();
        } catch (InterruptedException exception) {
            threads.forEach(Thread::interrupt);
            Thread.currentThread().interrupt();
            throw exception;
        }
        InheritanceIndex index = new InheritanceIndex();
        for (FileTask task : tasks) {
            if (task.failure != null) {
                throw task.failure;
            }
            if (task.result == null) {
                throw new IllegalStateException("Поток не вернул результат: " + task.file);
            }
            index.add(task.result);
        }
        index.print();
    }
}
