package eu.mihosoft.concurrencyutils;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * A task to be executed by an executor.
 * @param <T> the return type of the associated completable future
 */
public class Task <T> {

    private final String name;
    private final Callable<T>        callable;
    private final CompletableFuture<T> result;
    private final CompletableFuture<Telemetry> telemetry;
    private final long timestampCreated;

    // non-final/mutable
    private long timestampEnqueued;
    private long timestampStarted;
    private long timestampDequeued;

    private Task(String name, Callable<T> callable, CompletableFuture<T> result, CompletableFuture telemetry) {
        this.name      = name==null?"unnamed-task<"+System.identityHashCode(this)+">":name;
        this.callable  = callable;
        this.result    = result;
        this.telemetry = telemetry;
        this.timestampCreated = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public static <T> Task<T> newInstance(Callable<T> callable) {
        var t = new Task<>(null,callable, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    public static Task<Void> newInstance(Runnable callable) {
        var t = new Task<Void>(null, ()-> {
            callable.run();
            return null;
        }, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    public static <T> Task<T> newInstance(String name, Callable<T> callable) {
        var t = new Task<>(name,callable, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    public static Task<Void> newInstance(String name, Runnable callable) {
        var t = new Task<Void>(name, ()-> {
            callable.run();
            return null;
        }, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    public CompletableFuture<T> getResult() {
        return result;
    }

    public CompletableFuture<Telemetry> getTelemetry() {
        return telemetry;
    }

    T call() throws Exception {
        timestampStarted = System.currentTimeMillis();
        return callable.call();
    }

    void onEnqueued() {
        timestampEnqueued = System.currentTimeMillis();
    }

    void onDequeued() {
        timestampDequeued = System.currentTimeMillis();

        boolean executorLatencyValid = timestampStarted  > 0 && timestampEnqueued > 0;
        boolean totalLatencyValid    = timestampDequeued > 0 && timestampEnqueued > 0;
        boolean processingTimeValid  = timestampDequeued > 0 && timestampStarted  > 0;

        telemetry.complete(new Telemetry(
            timestampCreated,
            timestampEnqueued,
            timestampStarted,
            timestampDequeued,
            executorLatencyValid?timestampStarted-timestampEnqueued:0,
            totalLatencyValid?timestampDequeued-timestampEnqueued:0,
            processingTimeValid?timestampDequeued-timestampStarted:0
        ));
    }

    public record Telemetry(
        long timestampCreated,
        long timestampEnqueued,
        long timestampStarted,
        long timestampDequeued,
        long executorLatency, long totalLatency,long processingTime) {

        @Override
        public String toString() {
            return """
                Telemetry {
                    created   (ms since epoch):  %d
                    enqueued  (ms since epoch):  %d
                    started:  (ms since epoch):  %d
                    dequeued: (ms since epoch):  %d
                    executor-latency (ms):       %d
                    total-latency    (ms):       %d
                    processing-time  (ms):       %d
                }""".formatted(
                    timestampCreated, timestampEnqueued, timestampStarted, timestampDequeued,
                    executorLatency, totalLatency, processingTime
                );
        }
    }
}
