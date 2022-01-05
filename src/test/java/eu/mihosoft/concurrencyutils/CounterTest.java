package eu.mihosoft.concurrencyutils;

import eu.mihosoft.vmfactors.Counter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CounterTest {

    private static final int N = 1_000;

    @Test
    public void incTest() {

        Counter counter = Counter.newInstance();

        for(int i = 0; i < N; i++) {
            counter.inc();
        }

        assertEquals(N, counter.getValue());

    }

    @Test
    public void decTest() {

        Counter counter = Counter.newInstance();

        for(int i = 0; i < N; i++) {
            counter.dec();
        }

        assertEquals(-N, counter.getValue());

    }

    @Test
    public void restTest() {

        Counter counter = Counter.newInstance();

        assertEquals(0, counter.getValue());

        for(int i = 0; i < N; i++) {
            counter.inc();
        }

        assertEquals(N, counter.getValue());

        counter.reset();

        assertEquals(0, counter.getValue());

    }

}
