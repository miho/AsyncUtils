package eu.mihosoft.asyncutils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;

public class VirtualThreadUtilsTest {

    @Test
    public void threadFactoryTest() {
//        Thread.ofVirtual().factory();
        ThreadFactory tf = VirtualThreadUtils.newThreadFactory(true);
        tf.newThread(() -> System.out.println("Thread executing! ")).start();
    }

}
