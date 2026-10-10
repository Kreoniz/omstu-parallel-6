package common;

public final class WorkerCount {
    private WorkerCount() {}

    public static int fromArguments(String[] args) {
        int count = args.length < 2 ? Runtime.getRuntime().availableProcessors() : Integer.parseInt(args[1]);
        if (count <= 0) {
            throw new IllegalArgumentException("Количество потоков должно быть положительным");
        }
        return count;
    }
}
