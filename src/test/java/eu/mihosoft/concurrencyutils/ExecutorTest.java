package eu.mihosoft.concurrencyutils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

public class ExecutorTest {

    @RepeatedTest(10)
    public void executorStartAndStopTest() {

        final int N = ThreadLocalRandom.current().nextInt(1,  250 + 1 /*+1 since its exclusive*/);
        final int P = ThreadLocalRandom.current().nextInt(1,   32 + 1 /*+1 since its exclusive*/);

        System.out.println("Starting test");
        System.out.println("N: %d, P: %d".formatted(N, P));

        var completionCounter = new AtomicInteger();
        var cancellationCounter = new AtomicInteger();

        Executor executor = Executor.newInstance(P);

        var f = new CompletableFuture<Boolean>();

        executor.registerOnStateChanged(evt -> {
            log(evt.oldState().name()+"->"+evt.newState().name());

            if(evt.isTerminatedEvent()) {
                f.complete(true);
            }
        });

        executor.start();

        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(()-> {
            for(int i = 0; i < N; i++) {
                final int finalI = i;
                executor.submit(() -> sleep(100)).getResult().handleAsync((unused, throwable) -> {
                    if(throwable!=null) {
                        cancellationCounter.incrementAndGet();
                        log("cancelled: " + finalI);
                    } else {
                        completionCounter.incrementAndGet();
                        log("done:      " + finalI);
                    }
                    return null;
                });
            }
        });

        CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS).execute(()-> {
            log("cancelling executor");
            executor.cancel();
        });

        f.join(); // wait until finished

        int C = cancellationCounter.get();
        int D = completionCounter.get();
        int T = C+D;

        System.out.println("N: %d, CANCELLED: %d, DONE: %d, TOTAL: %d".formatted(N, C, D, T));

        Assertions.assertEquals(N, T);

    }

    public void log(String value) {
        // output
        System.out.println("["
            + new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss.SSS")
            .format(new Date()) + "]: " + value);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
