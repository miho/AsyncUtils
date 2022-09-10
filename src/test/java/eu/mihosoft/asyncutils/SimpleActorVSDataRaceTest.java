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
        }).awaitAll();

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
        }).awaitAll();

        System.out.println("increment calls submitted.");
        System.out.println("calling getValue()...");
        int value = a.call("getValue");
        System.out.println("N: %d, SUM: %d".formatted(N, value));

        // no data races occurred. numbers should match.
        assertEquals(N, value);
    }
}
