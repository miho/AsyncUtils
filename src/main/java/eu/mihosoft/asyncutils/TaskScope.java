/*
 * Copyright 2022 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * If you use this software for scientific research then please cite the following publication(s):
 *
 * M. Hoffer, C. Poliwoda, & G. Wittum. (2013). Visual reflection library:
 * a framework for declarative GUI programming on the Java platform.
 * Computing and Visualization in Science, 2013, 16(4),
 * 181–192. http://doi.org/10.1007/s00791-014-0230-y
 */
package eu.mihosoft.asyncutils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Task scope that enables structured concurrency for tasks.
 * A simple usage example (wait for <b>all</b> tasks to finish):
 *
 * <p>
 * {@snippet :
 * var results = Tasks.scope(s->{
 *   for(int i=0;i<N; i++){
 *     s.async(()->doSomethingThatTakesAWhile()); // runs concurrently
 *   }
 * }).awaitAll();
 *
 * // continues after all tasks have been executed
 * }
 * </p>
 * <p>
 * Another usage example (wait for <b>any</b> task to finish):
 *
 * {@snippet :
 * var results = Tasks.scope(s->{
 *   for(int i=0;i<N; i++){
 *     s.async(()->doSomethingThatTakesAWhile()); // runs concurrently
 *   }
 * }).awaitAny();
 *
 * // continues after one task has been executed
 * }
 * </p>
 * @author Michael Hoffer (info@michaelhoffer.de)
 */
public final class TaskScope {
    private final Executor executor;
    private final BlockingQueue<Task<?>> queue;
    private final String name;
    private volatile boolean accepting;

    private TaskScope(String name, int numThreads) {
        this(name, Executor.newInstance(numThreads));
    }

    private TaskScope(String name, Executor executor) {
        this.name = name==null?"unnamed-scope<"+System.identityHashCode(this)+">":name;
        TaskScope parent = null;
        if((parent = executor.scope.getAndSet(this))!=null) {
            throw new IllegalStateException("Executor is already in use by another scope: " + parent);
        }
        this.executor = executor;
        queue = new LinkedBlockingQueue<>();
        accepting = true;
    }

    /**
     *
     * @return name of this task scope
     */
    public String getName() {
        return name;
    }

    /**
     * Asynchronously executes the specified runnable (uses the internal executor).
     * @param r runnable to execute
     * @return task created by this method
     */
    public Task<Void> async(Runnable r) {
        return async(Task.newInstance(name+":task-%d".formatted(queue.size()), r));
    }

    /**
     * Asynchronously executes the specified runnable (uses the internal executor).
     * @param callable callable to execute
     * @param <V> return type of the specified callable
     * @return task created by this method
     */
    public <V> Task<V> async(Callable<V> callable) {
        return async(Task.newInstance(name+":task-%d".formatted(queue.size()), callable));
    }

    /**
     * Asynchronously executes the specified runnable (uses the internal executor).
     * @param t task to execute
     * @param <V> return type of the specified task
     * @return task created by this method
     */
    public <V> Task<V> async(Task<V> t) {
        if(!accepting) throw new RejectedExecutionException("This scope does not accept tasks.");
        try {
            queue.put(t);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return executor.submit(t);
    }

    /**
     * Cancels this task scope (the internal executor).
     * @see Executor#cancel()
     */
    public void cancel() {
        executor.cancel();
    }

    /**
     * Stops this task scope (the internal executor).
     * @see Executor#stop()
     */
    public void stop() {
        executor.stop();
    }

    /**
     * Stops this task scope (the internal executor).
     * @return future that can be used to wait for the termination
     */
    public CompletableFuture<?> stopAsync() {
        return executor.stopAsync();
    }

    /**
     * Cancels this task scope (the internal executor).
     * @return future that can be used to wait for the cancellation
     * @see Executor#cancelAsync()
     */
    public CompletableFuture<?> cancelAsync() {
        return executor.cancelAsync();
    }

    /**
     * Awaits all specified task scopes.
     * @return list of results of all completed tasks
     */
    public List<Task<?>> awaitAll() {

        var elements = tasks().stream().map(t -> t.getResult()).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(elements)
            .thenApply(v -> Arrays.stream(elements).map(e -> e.join()).toList()).join();

        return tasks();
    }

    /**
     * Waits for any of the tasks (just a single one is enough).
     * @return return value (of the first task that completes)
     */
    @SuppressWarnings("unchecked")
    public Task<?> awaitAny() {
        var elements = tasks().stream().map(t -> t.asFuture()).toArray(CompletableFuture[]::new);

        // return task that completes first
        return (Task<?>) CompletableFuture.anyOf(elements).join();
    }

    /**
     * Tasks contained in this scope.
     * @return list of tasks contained in this scope
     */
    public List<Task<?>> tasks() {
        return queue.stream().toList();
    }

    /**
     * Returns this tasks as future.
     * @return this tasks as future
     */
    public CompletableFuture<?> asFuture() {
        return CompletableFuture.allOf(tasks().stream().map(t -> t.getResult()).toArray(CompletableFuture[]::new));
    }

    /**
     * Creates a new task scope.
     * @param consumer consumer for creating tasks in this scope
     * @return task scope created by this method
     */
    public static TaskScope scope(Consumer<TaskScope> consumer) {
        return scope(null, 0 /*cached executor*/, consumer);
    }

    /**
     * Creates a new task scope.
     * @param numThreads number of threads to use
     * @param consumer consumer for creating tasks in this scope
     * @return task scope created by this method
     */
    public static TaskScope scope(int numThreads, Consumer<TaskScope> consumer) {
        return scope(null, numThreads, consumer);
    }

    /**
     * Creates a new task scope.
     * @param name name of the scope to create
     * @param consumer consumer for creating tasks in this scope
     * @return task scope created by this method
     */
    public static TaskScope scope(String name, Consumer<TaskScope> consumer) {
        return scope(name, 0/*cached executor*/, consumer);
    }

    /**
     * Creates a new task scope.
     * @param name name of the scope to create
     * @param consumer consumer for creating tasks in this scope
     * @param executor executor for running tasks in this scope (can only be used by one scope)
     * @return task scope created by this method
     */
    public static TaskScope scope(String name, Consumer<TaskScope> consumer, Executor executor) {
        var scope = new TaskScope(name, executor);
        scope.executor.start();
        try {
            consumer.accept(scope);
        } finally {
            scope.accepting = false;
            scope.executor.stopAsync();
        }

        return scope;
    }

    /**
     * Creates a new task scope.
     * @param name name of the scope to create
     * @param numThreads number of threads to use
     * @param consumer consumer for creating tasks in this scope
     * @return task scope created by this method
     */
    public static TaskScope scope(String name, int numThreads, Consumer<TaskScope> consumer) {

        var scope = new TaskScope(name, numThreads);
        scope.executor.startIfNotRunning();
        try {
            consumer.accept(scope);
        } finally {
            scope.accepting = false;
            scope.executor.stopAsync();
        }

        return scope;
    }
}
