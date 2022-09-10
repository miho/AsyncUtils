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

    /**
     * Returns the name of this task.
     * @return name of this task
     */
    public String getName() {
        return name;
    }

    /**
     * Creates a new task.
     * @param callable the callable to execute
     * @param <T> the return type of the callable
     * @return a new task created by this method
     */
    public static <T> Task<T> newInstance(Callable<T> callable) {
        var t = new Task<>(null,callable, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    /**
     * Creates a new task.
     * @param runnable the runnable to execute
     * @return a new task created by this method
     */
    public static Task<Void> newInstance(Runnable runnable) {
        var t = new Task<Void>(null, ()-> {
            runnable.run();
            return null;
        }, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    /**
     * Creates a new task.
     * @param name name of the task to create
     * @param callable callable to execute
     * @param <T> the return type of the callable
     * @return a new task created by this method
     */
    public static <T> Task<T> newInstance(String name, Callable<T> callable) {
        var t = new Task<>(name,callable, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    /**
     * Creates a new task.
     * @param name name of the task to create
     * @param runnable runnable to execute
     * @return a new task created by this method
     */
    public static Task<Void> newInstance(String name, Runnable runnable) {
        var t = new Task<Void>(name, ()-> {
            runnable.run();
            return null;
        }, new CompletableFuture<>(), new CompletableFuture<>());
        return t;
    }

    /**
     * Returns the result as completable future.
     * @return the result as completable future
     */
    public CompletableFuture<T> getResult() {
        return result;
    }

    /**
     * Returns the task as completable future.
     * @return the task as completable future
     */
    public CompletableFuture<Task<T>> asFuture() {
        return result.thenApply((r) -> this);
    }

    /**
     * Telemetry information as completable future.
     * @return telemetry information as completable future
     */
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

        telemetry.complete(new TelemetryImpl(
            timestampCreated,
            timestampEnqueued,
            timestampStarted,
            timestampDequeued,
            executorLatencyValid?timestampStarted-timestampEnqueued:0,
            totalLatencyValid?timestampDequeued-timestampEnqueued:0,
            processingTimeValid?timestampDequeued-timestampStarted:0
        ));
    }

    /**
     * Task telemetry information.
     */
    public sealed interface Telemetry permits TelemetryImpl {
        /**
         *
         * @return point in time at which the task has been created (milliseconds since unix epoch)
         */
        long getTimestampCreated();
        /**
         *
         * @return point in time at which the task has been enqueued (milliseconds since unix epoch)
         */
        long getTimestampEnqueued();
        /**
         *
         * @return point in time at which the task has been started (milliseconds since unix epoch)
         */
        long getTimestampStarted();
        /**
         *
         * @return point in time at which the task has been dequeued (milliseconds since unix epoch)
         */
        long getTimestampDequeued();
        /**
         *
         * @return executor latency (duration between enqueuing and starting the task, milliseconds)
         */
        long getExecutorLatency();
        /**
         *
         * @return total latency (duration between enqueuing and dequeuing the task, milliseconds)
         */
        long getTotalLatency();
        /**
         *
         * @return processing time (duration between starting and dequeuing the task, milliseconds)
         */
        long getProcessingTime();
    }

    private record TelemetryImpl (
        long timestampCreated,
        long timestampEnqueued,
        long timestampStarted,
        long timestampDequeued,
        long executorLatency, long totalLatency,long processingTime) implements Telemetry {

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

        @Override
        public long getExecutorLatency() {
            return executorLatency;
        }

        @Override
        public long getProcessingTime() {
            return processingTime;
        }

        @Override
        public long getTimestampCreated() {
            return timestampCreated;
        }

        @Override
        public long getTimestampDequeued() {
            return timestampDequeued;
        }

        @Override
        public long getTimestampEnqueued() {
            return timestampEnqueued;
        }

        @Override
        public long getTimestampStarted() {
            return timestampStarted;
        }

        @Override
        public long getTotalLatency() {
            return totalLatency;
        }
    }
}
