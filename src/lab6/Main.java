package lab6;

import common.InheritanceIndex;
import common.JavaSourceParser;
import common.ParsedSource;
import common.SourceFiles;
import common.WorkerCount;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {
    private record Job(Path file) {}
    private record Result(ParsedSource source, Exception failure) {}
    private static final Job POISON_PILL = new Job(null);

    private static void work(BlockingQueue<Job> jobs, BlockingQueue<Result> results) {
        try {
            while (true) {
                Job job = jobs.take();
                if (job == POISON_PILL) {
                    return;
                }
                Result result;
                try {
                    result = new Result(JavaSourceParser.parse(job.file()), null);
                } catch (Exception exception) {
                    result = new Result(null, exception);
                }
                results.put(result);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException("Использование: lab6.Main [папка Java-проекта] [число потоков]");
        }
        int workers = WorkerCount.fromArguments(args);
        var files = SourceFiles.find(Path.of(args.length == 0 ? "examples/project" : args[0]));
        BlockingQueue<Job> jobs = new ArrayBlockingQueue<>(workers);
        BlockingQueue<Result> results = new LinkedBlockingQueue<>();
        List<Thread> threads = new ArrayList<>();
        var index = new InheritanceIndex();
        Exception failure = null;
        try {
            for (int i = 0; i < workers; i++) {
                Thread thread = new Thread(() -> work(jobs, results), "worker-" + i);
                threads.add(thread);
                thread.start();
            }
            for (Path file : files) {
                jobs.put(new Job(file));
            }
            for (int i = 0; i < workers; i++) {
                jobs.put(POISON_PILL);
            }
            for (int i = 0; i < files.size(); i++) {
                Result result = results.take();
                if (result.failure() != null) {
                    if (failure == null) {
                        failure = result.failure();
                    }
                } else {
                    index.add(result.source());
                }
            }
        } finally {
            threads.forEach(Thread::interrupt);
            for (Thread thread : threads) {
                thread.join();
            }
        }
        if (failure != null) {
            throw failure;
        }
        index.print();
    }
}
