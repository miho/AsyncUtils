package eu.mihosoft.concurrencyutils;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleActorVSDataRaceTest {

    private static final int N = 1_000;
    private static final int P =    16;

    @Test
    public void simpleDataRaceTestMustFail() {

        // Counter is used to demonstrate data races
        // that occur by concurrently calling
        // methods of the counter object
        Counter counter = Counter.newInstance();

        // concurrently call 'inc' N times
        Tasks.group(P, g -> {
            for (int i = 0; i < N; i++) {
                g.async(() -> {
                    counter.inc(); // data race
                    return 0;
                });
            }
        }).await();

        System.out.println("increment calls submitted.");
        System.out.println("N: %d, SUM: %d".formatted(N, counter.getValue()));

        // since we produce data races, the values shouldn't match
        assertNotEquals(N, counter.getValue());

    }

    @Test
    public void simpleDataRaceTestReflectionActor() {

        System.out.println("starting");

        // we use an actor to prevent data races
        // that occur by concurrently calling
        // methods of the counter object
        Counter counter = Counter.newInstance();
        GenericActor<Counter> a = GenericActor.of(
            counter, Executor.newSerialInstance()
        );

        // we concurrently call the 'inc' method
        Tasks.group(P, g -> {
            for(int i = 0; i < N; i++) {
                g.async(() -> a.callAsync("inc")); // data race prevented by actor
            }
        }).await();

        System.out.println("increment calls submitted.");
        System.out.println("calling getValue()...");
        int value = a.call("getValue");
        System.out.println("N: %d, SUM: %d".formatted(N, value));

        // no data races occurred. numbers should match.
        assertEquals(N, value);
    }
}
