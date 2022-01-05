# ConcurrencyUtils
tasks, async await, actors and channels for java

WIP

```java
var counter = new AtomicInteger();

Runnable slowIncrement = () -> {
    sleep(500);
    var value = counter.incrementAndGet();
    // output
    System.out.println("["
        + new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss.SSS")
        .format(new Date()) + "]: " + value);
};

// sequential
System.out.println("starting sequential:");
for(int i = 0; i < N; i++) {
    slowIncrement.run();                  // runs sequentially
}
 
counter.set(0);

// concurrent
System.out.println("starting concurrent:");
Tasks.group(g -> {
    for(int i = 0; i < N; i++) {
       g.async(()-> slowIncrement.run()); // runs concurrently
    }
}).await();
```
