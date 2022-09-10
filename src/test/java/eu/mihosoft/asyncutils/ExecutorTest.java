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
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

import static eu.mihosoft.asyncutils.TaskGroupTest.log;

public class ExecutorTest {

    @RepeatedTest(100)
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

        var submittedF = new CompletableFuture<>();
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

            submittedF.complete(null);
        });

        submittedF.join();
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

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
