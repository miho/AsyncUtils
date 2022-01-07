package eu.mihosoft.concurrencyutils;

import vjavax.observer.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Task executor. In contrast to {@link java.util.concurrent.ExecutorService} this executor can be
 * reused after stopping it. It works on task objects that provide so-called telemetry data,
 * e.g., measurement of latency and processing time. Additionally, this executor supports backpressure
 * by blocking if tasks are submitted and there is no space left in the internal queue. By default,
 * the queue is unbounded ({@code bufferSize=Integer.MAX_VALUE}) and does not block. To enable
 * backpressure, manually set the buffer size (see {@link #newInstance(int, int)} and
 * {@link #newSerialInstance(int)}).
 */
public final class Executor {

    private final BlockingQueue<Task<?>> queue;
    private final int numThreads;
    private final ReentrantLock lock = new ReentrantLock();

    private final AtomicReference<State> state = new AtomicReference<>(State.TERMINATED);
    private final List<Consumer<ExecutorEvent>> stateChangedListeners = new ArrayList<>();
    private volatile ExecutorService executor;
    private volatile boolean serial;
    private volatile long terminationTimeout = Long.MAX_VALUE; // ms

    /**
     * Executor state. See the individual states for a detailed explanation.
     */
    public enum State {
        /**
         * The executor is currently starting, i.e., it creates the required threads
         * and initializes its execution engine.
         */
        STARTING,
        /**
         * The executor has been successfully started. Tasks can now be submitted for execution.
         */
        STARTED,

        /**
         * The executor is cancelling enqueued tasks. Tasks can't be submitted. Attempts to do so will
         * fail with a {@link java.util.concurrent.RejectedExecutionException}.
         */
        CANCELLING,
        /**
         * The executor has cancelled all enqueued tasks. Tasks can't be submitted. Attempts to do so will
         * fail with a {@link java.util.concurrent.RejectedExecutionException}.
         */
        CANCELLED,
        /**
         * The executor is currently shutting down. Tasks can't be submitted. Attempts to do so will
         * fail with a {@link java.util.concurrent.RejectedExecutionException}.
         */
        SHUTTING_DOWN,
        /**
         * The executor has been successfully shut down. There might still be unprocessed tasks but no
         * tasks can be submitted to the executor. Attempts to do so will
         * fail with a {@link java.util.concurrent.RejectedExecutionException}.
         */
        SHUTDOWN,
        /**
         * The executor is terminating, i.e., it will do its best to either process or cancel unprocessed
         * tasks within the specified time period depending on whether termination has been triggered by a
         * cancellation request or a request to regularly stop the executor. If the executor does not
         * terminate within the specified time period, it will throw a
         * {@link java.util.concurrent.TimeoutException}.
         *
         */
        TERMINATING,
        /**
         * The executor has been successfully terminated. The queue is fully empty and all threads have been
         * terminated.
         */
        TERMINATED,

        /**
         * An error occurred during starting, stopping/cancelling. The caller of the corresponding
         * {@link #start()}, {@link #stop()} or {@link #cancel()} methods will receive an exception.
         */
        ERROR;


    }

    record ExecutorEvent(Executor executor, State oldState, State newState) {

        /**
         *
         * @return
         */
        boolean isCancelledEvent() {
            return oldState == State.CANCELLING && newState == State.CANCELLED;
        }

        /**
         *
         * @return
         */
        boolean isTerminatedEvent() {
            return oldState == State.TERMINATING && newState == State.TERMINATED;
        }

        /**
         *
         * @return
         */
        boolean isShutdownEvent() {
            return oldState == State.SHUTTING_DOWN && newState == State.SHUTDOWN;
        }

        /**
         *
         * @return
         */
        boolean isStartedEvent() {
            return oldState == State.STARTING && newState == State.STARTED;
        }
    }


    /**
     * Constructor.
     *
     * @param numThreads number of threads to be used by this executor
     * @param bufferSize buffer size (bounded blocking queue, blocks if limit exceeded)
     */
    private Executor(int numThreads, int bufferSize) {
        if(numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be positive");
        }
        this.numThreads = numThreads;
        serial = numThreads == 1;

        queue = new LinkedBlockingQueue<>(bufferSize);
    }

    /**
     * Constructor.
     *
     * @param numThreads number of threads to be used by this executor
     */
    private Executor(int numThreads) {
        this(numThreads, Integer.MAX_VALUE);
    }

    /**
     * Registers a listener that is notified whenever the state of this executor changes.
     * @param l listener to register
     * @return subscription that allows to unregister the listener from this executor
     */
    public Subscription registerOnStateChanged(Consumer<ExecutorEvent> l) {
        stateChangedListeners.add(l);
        return ()->stateChangedListeners.remove(l);
    }

    /**
     * Starts this executor.
     */
    public void start() {
        try {
            setState(State.STARTING);
            lock.lock();
        } catch(Exception ex) {
            setState(State.ERROR);
            throw ex;
        }
        try {
            if (executor != null && !isTerminated() && !isShutdown()) {
                throw new RuntimeException("Stop this executor before starting it.");
            }
            executor = Executors.newFixedThreadPool(numThreads);
            setState(State.STARTED);
        } catch (Exception ex) {
            setState(State.ERROR);
            throw ex;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new executor.
     * @param numThreads number of threads to be created by the executor
     * @param bufferSize buffer size (bounded blocking queue, blocks if limit exceeded)
     * @return executor
     */
    public static Executor newInstance(int numThreads, int bufferSize) {
        return new Executor(numThreads, bufferSize);
    }

    /**
     * Creates a new executor.
     * @param bufferSize buffer size (bounded blocking queue, blocks if limit exceeded)
     * @return executor
     */
    public static Executor newSerialInstance(int bufferSize) {
        return new Executor(1, bufferSize);
    }

    /**
     * Creates a new executor.
     * @param numThreads number of threads to be created by the executor
     * @return executor
     */
    public static Executor newInstance(int numThreads) {
        return new Executor(numThreads, Integer.MAX_VALUE);
    }

    /**
     * Creates a new serial executor (preserves order of tasks, {@code numThreads == 1}).
     * @return serial executor
     */
    public static Executor newSerialInstance() {
        return new Executor(1, Integer.MAX_VALUE);
    }

    /**
     * Indicates whether this executor is currently accepting tasks.
     * @return {@code true} if this executor is currently accepting tasks; {@code false} otherwise
     */
    public boolean isAccepting() {
        lock.lock();
        try {
            return state.get()==State.STARTED;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Indicates whether this executor is currently running.
     * @return {@code true} if this executor is currently running; {@code false} otherwise
     */
    public boolean isRunning() {
        lock.lock();
        try {
            return state.get()!=State.TERMINATED;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Indicates whether this executor has been shut down.
     * @return {@code true} if this executor has been shut down; {@code false} otherwise
     */
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

    /**
     * Indicates whether this executor has been terminated.
     * @return {@code true} if this executor has been terminated; {@code false} otherwise
     */
    public boolean isTerminated() {
        lock.lock();
        try {
            var e = executor;
            if(e!=null) return e.isTerminated(); else return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Indicates whether this executor is a serial executor
     * (number of threads equals to one, order of tasks is preserved).
     * @return {@code true} if this executor is a serial executor; {@code false} otherwise
     */
    public boolean isSerial() {
        return serial;
    }

    /**
     * Submits a task to this executor.
     * @param r task to submit
     * @return task object
     */
    public Task<Void> submit(Runnable r) {
        return submit(Task.newInstance(r));
    }

    /**
     * Submits a task to this executor.
     * @param callable task to submit
     * @param <V> return type
     * @return task object allowing access to return value
     */
    public <V> Task<V> submit(Callable<V> callable) {
        return submit(Task.newInstance(callable));
    }

    /**
     * Submits a task to this executor.
     * @param t task to submit
     * @param <V> return type
     * @return task object allowing access to return value
     */
    public <V> Task<V> submit(Task<V> t) {

        var f = t.getResult();

        if(f.isDone()) {
            return t;
        }

        if(!isRunning()) {
            throw new RejectedExecutionException(
                "Start this executor before submitting tasks"
            );
        }

        try {
            queue.put(t);
            t.onEnqueued();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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

    /**
     * Cancels all tasks currently submitted to this executor, shuts down this executor and finally terminates it.
     */
    public void cancel() {
        try {
            setState(State.CANCELLING);
            lock.lock();
        } catch(Exception ex) {
            setState(State.ERROR);
            throw ex;
        }
        try {
            queue.stream().forEach(t -> {
                var f = t.getResult();
                f.cancel(true);
                t.onDequeued();
            });
            setState(State.CANCELLED);
            setState(State.SHUTTING_DOWN);
            if(executor!=null && isRunning()) executor.shutdownNow(); // TOTO should we process remaining tasks?
            queue.clear();
            setState(State.SHUTDOWN);
            terminating();
            executor = null;
        } catch(Exception ex) {
            setState(State.ERROR);
            throw ex;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels all tasks currently submitted to this executor, shuts down this executor and completes if it
     * terminates.
     * @return future that is completed when the executor has been cancelled, shut down and terminated
     */
    public CompletableFuture<?> cancelAsync() {
        return CompletableFuture.supplyAsync(()->{
            cancel();
            return null;
        });
    };

    /**
     * Stops this executor (waits until previously submitted tasks are executed and the executor has been fully shut down and terminated).
     */
    public void stop() {
        try {
            setState(State.SHUTTING_DOWN);
            lock.lock();
        } catch(Exception ex) {
            setState(State.ERROR);
            throw ex;
        }
        try {
            if (executor != null && isRunning()) executor.shutdown();
            asFuture().orTimeout(terminationTimeout, TimeUnit.MILLISECONDS).join();
            setState(State.SHUTDOWN);
            terminating(); // should already be shut down
        } catch(Exception ex) {
            setState(State.ERROR);
        } finally {
            lock.unlock();
        }
    }


    /**
     * Stops this executor (waits until previously submitted tasks are executed and the executor has been fully shut down and terminated).
     *
     * @return future that is completed when the executor has been shut down and terminated
     */
    public CompletableFuture<?> stopAsync() {
        return CompletableFuture.supplyAsync(()->{
            stop();
            return null;
        });
    }

    /**
     * Sets the termination timeout in milliseconds.
     * @param timeout termination timeout in milliseconds
     * @return this executor
     */
    public Executor setTerminationTimeout(long timeout) {
        this.terminationTimeout = terminationTimeout;
        return this;
    }

    public long getTerminationTimeout() {
        return terminationTimeout;
    }

    /**
     * Awaits tasks to be executed.
     */
    private void await() {
        asFuture().join();
    }

    /**
     * Returns submitted tasks as future that is completed after all tasks currently present in the queue
     * have been executed.
     * @return future that is completed after all tasks have been executed
     */
    CompletableFuture<Void> asFuture() {

        var elements = queue.toArray();
        var futures = new CompletableFuture[elements.length];

        int i = 0;
        for(var e : elements) {
            futures[i++] = ((Task)e).getResult();
        }

        return CompletableFuture.allOf(futures);
    }

    private void terminating() {
        lock.lock();
        try {
            setState(State.TERMINATING);
            boolean success = executor.awaitTermination(terminationTimeout, TimeUnit.MILLISECONDS);
            if (success) {
                setState(State.TERMINATED);
            } else {
                setState(State.ERROR);
                throw new TimeoutException("Termination failed");
            }
        } catch(InterruptedException ex) {
            setState(State.ERROR);
            Thread.currentThread().interrupt();
        } catch(Exception ex) {
            setState(State.ERROR);
            throw new RuntimeException(ex);
        } finally {
            executor = null;
            lock.unlock();
        }
    }

    private void setState(State s) {
        var prev = state.getAndSet(s);

        if(s != prev) {
            CompletableFuture.runAsync(() -> stateChangedListeners.parallelStream().
                filter(l -> l != null).forEach(l -> l.accept(new ExecutorEvent(this, prev, s)))).join();
        }
    }
}
