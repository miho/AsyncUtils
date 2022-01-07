# AsyncUtils
tasks, async, await, actors and channels for java

WIP

## Structured concurrency with Task Groups

Consider the following code:


```java
// sequential
System.out.println("starting sequential:");
for(int i = 0; i < N; i++) {
    doSomethingThatTakesAWhile();       // runs sequentially
}
```

Java offers multiple APIs to execute methods concurrently. But these APIs are a little baroque.

Here's how we can perform the method calls inside the loop concurrently:

```java
// concurrent
System.out.println("starting concurrent:");
Tasks.group(g -> {
    for(int i = 0; i < N; i++) {
       g.async(()->doSomethingThatTakesAWhile()); // runs concurrently
    }
}).await();

// continues after all tasks have been executed
```

Of course, we could use Streams, Fork-Join and Futures. But either the APIs require a lot of boilerplate code
or they lack features such as control over how exceptions are handled or how to specify how many threads should be used to process the tasks.


