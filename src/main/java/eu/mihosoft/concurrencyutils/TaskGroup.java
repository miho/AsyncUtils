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
package eu.mihosoft.concurrencyutils;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class TaskGroup {
    private final Executor executor;
    private final BlockingQueue<Task<?>> queue;
    private final String name;
    private volatile boolean accepting;

    private TaskGroup(String name, int numThreads) {
        this.name = name==null?"unnamed-group<"+System.identityHashCode(this)+">":name;
        if(numThreads<1) {
            throw new IllegalArgumentException("Number of threads must be positive");
        }
        executor = Executor.newInstance(numThreads);
        queue = new LinkedBlockingQueue<>();
        accepting = true;
    }

    public String getName() {
        return name;
    }

    public Task<Void> async(Runnable r) {
        return async(Task.newInstance(name+":task-%d".formatted(queue.size()), r));
    }

    public <V> Task<V> async(Callable<V> r) {
        return async(Task.newInstance(name+":task-%d".formatted(queue.size()), r));
    }

    public <V> Task<V> async(Task<V> t) {
        if(!accepting) throw new RejectedExecutionException("This group does not accept tasks.");
        try {
            queue.put(t);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return executor.submit(t);
    }

    public void cancel() {
        executor.cancel();
    }

    public void stop() {
        executor.stop();
    }

    public List<Task<?>> await() {
        asFuture().join();
        return tasks();
    }

    public List<Task<?>> tasks() {
        return queue.stream().toList();
    }

    public CompletableFuture<?> asFuture() {
        return executor.asFuture();
    }

    public static TaskGroup group(Consumer<TaskGroup> consumer) {
        return group(null, Runtime.getRuntime().availableProcessors()-1, consumer);
    }

    public static TaskGroup group(int numThreads, Consumer<TaskGroup> consumer) {
        return group(null, numThreads, consumer);
    }

    public static TaskGroup group(String name, Consumer<TaskGroup> consumer) {
        return group(name, Runtime.getRuntime().availableProcessors()-1, consumer);
    }

    public static TaskGroup group(String name, int numThreads, Consumer<TaskGroup> consumer) {

        var fg = new TaskGroup(name, numThreads);
        fg.executor.start();
        try {
            consumer.accept(fg);
        } finally {
            fg.accepting = false;
            fg.executor.stopAsync();
        }

        return fg;
    }
}
