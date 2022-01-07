/*
 * Copyright 2022 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * If you use this software for scientific research then please cite the following publication(s):
 *
 * M. Hoffer, C. Poliwoda, & G. Wittum. (2013). Visual reflection library:
 * a framework for declarative GUI programming on the Java platform.
 * Computing and Visualization in Science, 2013, 16(4),
 * 181–192. http://doi.org/10.1007/s00791-014-0230-y
 */
package eu.mihosoft.asyncutils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

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
