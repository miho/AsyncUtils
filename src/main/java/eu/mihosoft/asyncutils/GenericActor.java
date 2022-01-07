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

import java.beans.Expression;
import java.util.Arrays;
import java.util.List;

public class GenericActor<T> {

    private final Object object;
    private final Executor executor;
    private final String name;

    private GenericActor(String name, T object, Executor executor) {
        this.name = name==null?"unnamed-actor<"+System.identityHashCode(this)+">":name;
        this.object = object;

        if(!executor.isSerial()) {
            throw new IllegalArgumentException("Specified executor is not serial");
        }

        this.executor = executor;
    }

    public static <T> GenericActor<T> of(T object) {
        var executor = Executor.newSerialInstance();
        executor.start();
        return new GenericActor<>(null, object, executor);
    }

    public static <T> GenericActor<T> of(T object, Executor executor) {
        if(!executor.isRunning()) executor.start();
        return new GenericActor<>(null, object, executor);
    }

    public <V> Task<V> callAsync(String method, List<Object> args) {

        Expression methodCallExpression = new Expression(object, method, args.toArray(new Object[args.size()]));

        return executor.submit(Task.newInstance(
            name+":method<"+method+","+System.identityHashCode(methodCallExpression)+">",
            () -> {
                methodCallExpression.execute();
                return (V)methodCallExpression.getValue();
            }
        ));
    }

    public <V> Task<V> callAsync(String method, Object... args) {
        return callAsync(method, Arrays.asList(args));
    }

    public <V> V call(String method, List<Object> args) {
        return (V)callAsync(method, args).getResult().join();
    }

    public <V> V call(String method, Object... args) {
        return (V)callAsync(method, Arrays.asList(args)).getResult().join();
    }
}
