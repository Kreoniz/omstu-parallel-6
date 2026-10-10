package lab4;

import common.InheritanceIndex;
import common.JavaSourceParser;
import common.SourceFiles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Использование: lab4.Main [папка Java-проекта]");
        }
        var files = SourceFiles.find(Path.of(args.length == 0 ? "examples/project" : args[0]));
        var index = new InheritanceIndex();
        var lock = new ReentrantLock();
        var completion = new CountDownLatch(files.size());
        var failures = new ConcurrentLinkedQueue<Exception>();
        List<Thread> threads = new ArrayList<>();

        for (Path file : files) {
            threads.add(Thread.ofVirtual().name("parse-" + file.getFileName()).start(() -> {
                try {
                    var source = JavaSourceParser.parse(file);
                    lock.lock();
                    try {
                        index.add(source);
                    } finally {
                        lock.unlock();
                    }
                } catch (Exception exception) {
                    failures.add(exception);
                } finally {
                    completion.countDown();
                }
            }));
        }
        try {
            completion.await();
        } catch (InterruptedException exception) {
            threads.forEach(Thread::interrupt);
            Thread.currentThread().interrupt();
            throw exception;
        }
        if (!failures.isEmpty()) {
            throw failures.peek();
        }
        index.print();
    }
}
