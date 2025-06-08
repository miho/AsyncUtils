package eu.mihosoft.asyncutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExecutorTerminationTimeoutTest {
    @Test
    public void setAndGetTerminationTimeout() {
        Executor executor = Executor.newInstance(1);
        long timeout = 12345L;
        executor.setTerminationTimeout(timeout);
        assertEquals(timeout, executor.getTerminationTimeout());
    }
}
