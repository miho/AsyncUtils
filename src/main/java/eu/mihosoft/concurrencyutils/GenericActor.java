package eu.mihosoft.concurrencyutils;

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
