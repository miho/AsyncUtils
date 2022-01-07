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
