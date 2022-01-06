package eu.mihosoft.concurrencyutils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.lang.Thread.sleep;

public class TaskGroupTest {

    private static final int N = 10;

    @Test
    public void taskGroupTest() {

        var counter = new AtomicInteger();

        Runnable slowIncrement = () -> {
            sleep(500);
            var value = counter.incrementAndGet();
            // output
            System.out.println("["
                + new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss.SSS")
                .format(new Date()) + "]: " + value);
        };

        long t1 = System.nanoTime();
        // sequential
        System.out.println("starting sequential:");
        for(int i = 0; i < N; i++) {
            slowIncrement.run();                  // runs sequentially
        }

        long t2 = System.nanoTime();

        counter.set(0);

        // concurrent
        System.out.println("starting concurrent:");
        Tasks.group(g -> {
            for(int i = 0; i < N; i++) {
               g.async(slowIncrement); // runs concurrently
            }
        }).await();

        long t3 = System.nanoTime();

        long sequentialDuration = (long)((t2-t1)*1e-6)/*ms*/;
        long concurrentDuration = (long)((t3-t2)*1e-6)/*ms*/;

        // concurrent version should run at least twice as fast
        Assertions.assertTrue(concurrentDuration<sequentialDuration/2);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
