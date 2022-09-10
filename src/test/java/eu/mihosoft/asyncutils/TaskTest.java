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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static eu.mihosoft.asyncutils.TaskScopeTest.sleep;

public class TaskTest {

    private static int N = 100;

    @Test
    public void taskAwaitAnyTest() {

        var executor = Executor.newInstance(0);
        executor.start();

        var taskIdx = 3;
        var tasks = new Task[N];

        for(int i = 0; i < N; i++) {
            System.out.println("submitting task " + i);
            final int finalI = i;
            var task = Task.newInstance(()->{
                sleep(finalI==taskIdx?0:10_000); // we should receive the result of task 'taskIdx' first
                return finalI;
            }); // runs concurrently
            tasks[i] = task;

            executor.submit(task);
        }

        var completedIdx = Tasks.awaitAny(tasks);

        Assertions.assertEquals(taskIdx, completedIdx);
    }

    @Test
    public void awaitAllTest() {
        var executor = Executor.newInstance(0);
        executor.start();

        var tasks = new Task[N];

        List<Integer> expected = new ArrayList<>();

        System.out.println("> submitting tasks");
        for(int i = 0; i < N; i++) {
            System.out.println("  -> submitting task " + i);
            expected.add(i);
            final int finalI = i;
            var task = Task.newInstance(()->{
                sleep(1_000);
                return finalI;
            }); // runs concurrently
            tasks[i] = task;

            executor.submit(task);
        }

        System.out.println("> awaiting all tasks");
        List<Integer> result = Tasks.awaitAll(tasks);

        Assertions.assertEquals(N, result.size());
        Assertions.assertEquals(expected, result);
    }
}
