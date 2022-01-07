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
