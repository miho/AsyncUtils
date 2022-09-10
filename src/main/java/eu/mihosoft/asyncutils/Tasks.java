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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Task utilities.
 */
public final class Tasks {

    private Tasks() {
        throw new AssertionError("Don't instantiate me!");
    }

    /**
     * Creates a new task group.
     * @param consumer consumer for creating tasks in this group
     * @return task group created by this method
     */
    public static TaskGroup group(Consumer<TaskGroup> consumer) {
        return TaskGroup.group(consumer);
    }

    /**
     * Creates a new task group.
     * @param numThreads number of threads to use
     * @param consumer consumer for creating tasks in this group
     * @return task group created by this method
     */
    public static TaskGroup group(int numThreads, Consumer<TaskGroup> consumer) {
        return TaskGroup.group(numThreads, consumer);
    }

    /**
     * Awaits all specified task groups.
     * @param groups groups to wait for
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> awaitAll(TaskGroup... groups) {
        var elements = Arrays.stream(groups).map(g -> g.asFuture()).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(elements)
            .thenApply(v -> Arrays.stream(elements).map(e -> (T) e.join()).toList()).join();
    }

    /**
     * Waits for any of the specified task groups (just a single one is enough).
     * @param groups groups to wait for
     * @param <T> return type of the first task that completes
     * @return return value (of the first task that completes)
     */
    @SuppressWarnings("unchecked")
    public static <T> T awaitAny(TaskGroup... groups) {
        var elements = Arrays.stream(groups).map(g -> g.asFuture()).toArray(CompletableFuture[]::new);
        return (T) CompletableFuture.anyOf(elements).join();
    }

    /**
     * Awaits all specified task groups.
     * @param groups groups to wait for
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> awaitAll(Task... groups) {
        var elements = Arrays.stream(groups).map(g -> g.getResult()).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(elements)
            .thenApply(v -> Arrays.stream(elements).map(e -> (T) e.join()).toList()).join();
    }

    /**
     * Waits for any of the specified task groups (just a single one is enough).
     * @param groups groups to wait for
     * @param <T> return type of the first task that completes
     * @return return value (of the first task that completes)
     */
    @SuppressWarnings("unchecked")
    public static <T> T awaitAny(Task... groups) {
        var elements = Arrays.stream(groups).map(g -> g.getResult()).toArray(CompletableFuture[]::new);
        return (T) CompletableFuture.anyOf(elements).join();
    }
}
