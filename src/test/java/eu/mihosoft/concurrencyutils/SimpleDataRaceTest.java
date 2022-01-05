package eu.mihosoft.concurrencyutils;

import eu.mihosoft.concurrencyutils.Executor;
import eu.mihosoft.concurrencyutils.GenericActor;
import eu.mihosoft.concurrencyutils.Tasks;
import eu.mihosoft.vmfactors.Counter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleDataRaceTest {

    private static final int N = 1_000;
    private static final int P =    16;

    @Test
    public void simpleDataRaceTestMustFail() {

        Counter counter = Counter.newInstance();

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

        Counter counter = Counter.newInstance();
        GenericActor<Counter> a = GenericActor.of(
            counter, Executor.newSerialInstance()
        );

        Tasks.group(P, g -> {
            for(int i = 0; i < N; i++) {
                g.async(() -> a.callAsync("inc"));
            }
        }).await();

        System.out.println("increment calls submitted.");
        System.out.println("calling getValue()...");
        int value = a.call("getValue");
        System.out.println("N: %d, SUM: %d".formatted(N, value));

        assertEquals(N, value);
    }

    @Test
    public void simpleDataRaceTest() {

//        Counter counter = Counter.newInstance();
//        CounterActor a  = counter.asActor();
//
//        for(int i = 0; i < N; i++) {
//            executor.execute(() -> a.inc());
//        }
//
//        assertEquals(N, a.getValue());

    }
}
