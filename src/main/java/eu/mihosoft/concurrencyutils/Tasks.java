package eu.mihosoft.concurrencyutils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class Tasks {

    public static TaskGroup group(Consumer<TaskGroup> consumer) {
        return TaskGroup.group(consumer);
    }

    public static TaskGroup group(int numThreads, Consumer<TaskGroup> consumer) {
        return TaskGroup.group(numThreads, consumer);
    }

    public static void awaitAll(TaskGroup... groups) {
        var elements = groups;
        CompletableFuture[] futures = new CompletableFuture[elements.length];
        System.arraycopy(elements, 0, futures, 0, elements.length);

        CompletableFuture.allOf(futures).join();
    }

    public static <T> T awaitAny(TaskGroup... groups) {
        var elements = groups;
        CompletableFuture[] futures = new CompletableFuture[elements.length];
        System.arraycopy(elements, 0, futures, 0, elements.length);

        return (T)CompletableFuture.anyOf(futures).join();
    }
}
