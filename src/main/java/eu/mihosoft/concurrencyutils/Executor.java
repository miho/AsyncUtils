package eu.mihosoft.concurrencyutils;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task executor.
 */
public final class Executor {
    private final BlockingQueue<Task<?>> queue;
    private final int numThreads;
    private volatile ExecutorService executor;
    private volatile boolean serial;
    private final ReentrantLock lock = new ReentrantLock();

    private Executor(int numThreads, int bufferSize) {
        if(numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be positive");
        }
        this.numThreads = numThreads;
        serial = numThreads == 1;

        queue = new LinkedBlockingQueue<>(bufferSize);
    }

    private Executor(int numThreads) {
        this(numThreads, Integer.MAX_VALUE);
    }

    /**
     * Starts this executor.
     */
    public void start() {
        lock.lock();
        try {
            if (executor != null && !isTerminated() && !isShutdown()) {
                throw new RuntimeException("Stop this executor before starting it.");
            }
            executor = Executors.newFixedThreadPool(numThreads);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new executor.
     * @param numThreads
     * @param bufferSize
     * @return
     */
    public static Executor newInstance(int numThreads, int bufferSize) {
        return new Executor(numThreads, bufferSize);
    }

    public static Executor newSerialInstance(int bufferSize) {
        return new Executor(1, bufferSize);
    }

    public static Executor newInstance(int numThreads) {
        return new Executor(numThreads, Integer.MAX_VALUE);
    }

    public static Executor newSerialInstance() {
        return new Executor(1, Integer.MAX_VALUE);
    }

    public boolean isRunning() {
        lock.lock();
        try {
            return !isShutdown() && !isTerminated();
        } finally {
            lock.unlock();
        }
    }

    public boolean isShutdown() {
        lock.lock();
        try {
            var e = executor;
            if (e != null) return e.isShutdown();
            else return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isTerminated() {
        lock.lock();
        try {
            var e = executor;
            if(e!=null) return e.isTerminated(); else return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isSerial() {
        return serial;
    }

    public Task<Void> submit(Runnable r) {
        return submit(Task.newInstance(r));
    }

    public <V> Task<V> submit(Callable<V> callable) {
        return submit(Task.newInstance(callable));
    }

    public <V> Task<V> submit(Task<V> t) {

        var f = t.getResult();

        if(f.isDone()) {
            return t;
        }

        try {
            queue.put(t);
            t.onEnqueued();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        if(!isRunning()) {
            throw new RejectedExecutionException(
                "Start this executor before submitting tasks"
            );
        }

        executor.execute(() -> {
            try {
                if(!f.isDone()) {
                    try {
                        f.complete(t.call());
                    } catch (Throwable throwable) {
                        f.completeExceptionally(throwable);
                    }
                }
            } finally {
                queue.remove(t);
                t.onDequeued();
            }
        });

        return t;
    }

    public void cancel() {
        lock.lock();
        try {
            if(executor!=null && isRunning()) executor.shutdownNow();
            queue.stream().forEach(t->{
                var f = t.getResult();
                f.cancel(true);
                t.onDequeued();
            });
            executor = null;
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            if (executor != null && isRunning()) executor.shutdown();
            asFuture().join();
            executor = null;
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<?> stopAsync() {
        return CompletableFuture.supplyAsync(()->{
            stop();
            return null;
        }, Executors.newSingleThreadExecutor());
    };

    private void await() {
        asFuture().join();
    }

    CompletableFuture<Void> asFuture() {

        var elements = queue.toArray();
        var futures = new CompletableFuture[elements.length];

        int i = 0;
        for(var e : elements) {
            futures[i++] = ((Task)e).getResult();
        }

        return CompletableFuture.allOf(futures);
    }

}
