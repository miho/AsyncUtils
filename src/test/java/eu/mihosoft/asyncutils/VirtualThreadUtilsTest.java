package eu.mihosoft.asyncutils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VirtualThreadUtilsTest {

    @Test
    public void threadFactoryTest() throws InterruptedException {
        ThreadFactory tf = VirtualThreadUtils.newThreadFactory(true);

        Thread thread = tf.newThread(() -> {
            // no-op
        });

        boolean isVirtual = VirtualThreadUtils.isVirtual(thread);

        thread.start();
        thread.join();

        assertEquals(VirtualThreadUtils.areVirtualThreadsSupported(), isVirtual);
    }

}
