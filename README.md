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

## Structured concurrency with Task Groups

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
Tasks.group(g -> {
    for(int i = 0; i < N; i++) {
       g.async(()->doSomethingThatTakesAWhile()); // runs concurrently
    }
}).awaitAll();

// continues after all tasks have been executed
```

Of course, we could use Streams, Fork-Join APIs and Futures. But either the APIs require a lot of boilerplate code
or they lack features such as control over how exceptions are handled or how to specify how many threads should be 
used to process the tasks.


