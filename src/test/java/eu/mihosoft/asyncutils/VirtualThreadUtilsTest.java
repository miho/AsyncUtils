package eu.mihosoft.asyncutils;

import org.junit.jupiter.api.Test;

import java.beans.Expression;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadUtilsTest {

    @Test
    public void threadFactoryTest() {
        ThreadFactory tf = VirtualThreadUtils.newThreadFactory(true);
        tf.newThread(() -> System.out.println("Thread executing! "
                + VirtualThreadUtils.isVirtual(Thread.currentThread()))).start();
    }

}
