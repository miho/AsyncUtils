package eu.mihosoft.asyncutils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static eu.mihosoft.asyncutils.TaskGroupTest.sleep;

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
