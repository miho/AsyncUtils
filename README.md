# AsyncUtils
tasks, async, await, actors and channels for java

This project tries to explore several approaches to simplify async/concurrent programming in Java.
AsyncUtils basically consits of distilled versions of patterns that I implemented over the past years. 
The purpose isn't best-in class performance but rather reliability. 

Sharing mutable state is usually avoided in concurrent programming. We use actors to reduce the negative
impact of shared mutable state. Currently, there's only a reflection based actor. It will serve as 
prototype for [VMF](https://github.com/miho/VMF) actors currently in development.

Furthermore, this project uses virtual threads if available (either via `--enable-preview` or if virtual threads are officially supported). 
The availability of the API is automatically detected at runtime. See [Project Loom](https://openjdk.java.net/projects/loom/) for details on virtual threads and the progress on structured concurrency.


*WARNING:* WIP, the API might change over time.

## Structured concurrency with TaskScopes

Consider the following code:


```java
// sequential
System.out.println("starting sequential:");
for(int i = 0; i < N; i++) {
    doSomethingThatTakesAWhile();       // runs sequentially
}
```

Java offers multiple APIs to execute methods concurrently. But these APIs are a little baroque. AsyncUtils 
provides simpler APIs for that purpose.

Here's how we can perform the method calls inside the loop concurrently:

```java
// concurrent
System.out.println("starting concurrent:");
Tasks.scope(s -> {
    for(int i = 0; i < N; i++) {
       s.async(()->doSomethingThatTakesAWhile()); // runs concurrently
    }
}).awaitAll();

// continues after all tasks have been executed
```

Of course, we could use Streams, Fork-Join APIs and Futures. But either the APIs require a lot of boilerplate code
or they lack features such as control over how exceptions are handled or how to specify how many threads should be 
used to process the tasks.

Task objects provide sophisiticated telemetry information:

```java
var t = executor.submit(()->{...});

t.getTelemetry().thenAccept((t)->{
    System.out.println(t);
});

t.getResult().join(); // wait for the task to finish

```

## Preventing data races with generic Actors

A generic actor class that uses a serial execution model to process the method calls (i.e., the method calls are executed in the order they are received). Each method call returns a task object that provides the same telemetry information as shown above.

The following example shows how to use this class to prevent data races for a simple counter class.

First, consider the following counter class:

```java
class Counter {
  private int v;
  
  public void inc() {v++;}
  public void dec() {v--;}
  public int getValue() {return v;}
  }

Calling the methods of this class concurrently will result in data races. the code snippet below demonstrates this:

```java
int N = 1000;
var counter = new Counter();
Tasks.scope(scope -> {
for (int i = 0; i < N; i++) {
  scope.async(() -> {
    counter.inc(); // data race
  });
}
}).awaitAll();

// since we produce data races, the values shouldn't match
assertNotEquals(N, counter.getValue());
```

This can simply be prevented by using an actor instead:

```java
int N = 1000;
var counter = GenericActor.newInstance(new Counter());
Tasks.scope(scope -> {
  for (int i = 0; i < N; i++) {
    scope.async(() -> {
      counter.callAsync("inc");
    });
  }
}).awaitAll();

// since we produce no data races, the values should match
assertEquals(N, counter.getValue());
```




