package eu.mihosoft.concurrencyutils;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ExecutorTest {

    private final int N = 10;
    private final int P = 4;

    @Test
    public void executorStartAndStopTest() {
        Executor executor = Executor.newInstance(P);

        executor.registerOnStateChanged((state, state2) -> {
            log(state.name()+"->"+state2.name());
        });

        executor.start();

        CompletableFuture.delayedExecutor(1000, TimeUnit.MILLISECONDS).execute(()-> {
            for(int i = 0; i < N; i++) {
                executor.submit(() -> sleep(1000)).getResult().handleAsync((unused, throwable) -> {
                    if(throwable!=null) log("cancelled"); else log("> done.");
                    return null;
                });
            }
        });

        CompletableFuture.delayedExecutor(3000, TimeUnit.MILLISECONDS).execute(()-> {
            executor.cancel();
        });

        sleep(30_000);

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
