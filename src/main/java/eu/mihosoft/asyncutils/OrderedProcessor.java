package eu.mihosoft.asyncutils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.*;

/**
 * This class provides a way to process items concurrently while preserving
 * the order of the items. It manages multiple processing threads and ensures
 * that items are retrieved in the same order they were added, even if they
 * were processed out of order.
 *
 * @param <Tin>  The type of the item to be processed.
 * @param <Tout> The type of the item to be returned after processing.
 */
public class OrderedProcessor<Tin, Tout> {

    /**
     * The processing function to be applied to each item.
     */
    private final Function<Tin, Tout> processingFunction;

    /**
     * The callback function to be called when a new item is processed.
     */
    private Consumer<RetrieveResult<Tout>> callback;

    /**
     * The list of processing threads.
     */
    private final List<Thread> processors = new ArrayList<>();

    /**
     * The priority queue holding processed items.
     */
    private final PriorityQueue<Item<Tout>> outputQueue = new PriorityQueue<>();

    /**
     * The queue holding items to be processed.
     */
    private final BlockingQueue<Item<Tin>> inputQueue = new LinkedBlockingQueue<>();

    // Locks and conditions for input and output operations synchronization
    private final ReentrantLock inputLock = new ReentrantLock();
    private final Condition inputCondition = inputLock.newCondition();
    private final ReentrantLock outputLock = new ReentrantLock();
    private final Condition outputCondition = outputLock.newCondition();

    // Atomic variables for sequence numbers and shutdown status
    private final AtomicLong nextSeqNum = new AtomicLong();
    private final AtomicInteger nextOutputNum = new AtomicInteger();
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Constructs an OrderedProcessor object.
     *
     * @param numProcessors      The number of concurrent processors (threads) to run.
     * @param processingFunction The processing function to be applied to each item.
     * @param callback           The callback function to register.
     */
    public OrderedProcessor(int numProcessors, Function<Tin, Tout> processingFunction, Consumer<RetrieveResult<Tout>> callback) {
        this.processingFunction = processingFunction;
        this.callback = callback;

        // Start processing threads
        for (int i = 0; i < numProcessors; i++) {
            Thread processor = new Thread(this::process);
            processors.add(processor);
            processor.start();
        }
    }

    /**
     * Adds an item to be processed.
     *
     * @param data The item to be processed.
     */
    public void addData(Tin data) {
        inputLock.lock();
        try {
            inputQueue.add(new Item<>(nextSeqNum.getAndIncrement(), data));
            inputCondition.signal(); // Notify a processing thread that an item is available
        } finally {
            inputLock.unlock();
        }
    }

    /**
     * Retrieves processed items.
     *
     * @param maxElements The maximum number of items to retrieve.
     * @param getAvailable Whether to retrieve available items without waiting.
     * @return RetrieveResult object containing the retrieved items and the number of remaining items.
     * @throws RuntimeException if the thread is interrupted while waiting.
     */
    public RetrieveResult<Tout> retrieveData(int maxElements, boolean getAvailable) {
        outputLock.lock();
        try {
            RetrieveResult<Tout> result = new RetrieveResult<>(null,0);
            while (result.data.size() < maxElements && (!outputQueue.isEmpty() || !isShutdown.get())) {
                if (!outputQueue.isEmpty() && outputQueue.peek().seqNum == nextOutputNum.get()) {
                    result.data.add(outputQueue.poll().data);
                    nextOutputNum.incrementAndGet();
                    if (getAvailable) break;
                } else if (!isShutdown.get()) {
                    if (getAvailable) break;
                    outputCondition.await(); // Wait for an item to be processed
                }
            }
            result.remaining = outputQueue.size();
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            outputLock.unlock();
        }
    }

    /**
     * Shuts down the OrderedProcessor, ensuring that all processing threads are stopped.
     */
    public void shutdown() {
        isShutdown.set(true);
        inputCondition.signalAll(); // Notify all processing threads to stop
        for (Thread processor : processors) {
            try {
                processor.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Registers a callback function to be called when a new item is processed.
     *
     * @param callback The callback function to register.
     */
    public void setOnItemProcessed(Consumer<RetrieveResult<Tout>> callback) {
        this.callback = callback;
    }

    /**
     * Processes the items added to the input queue.
     * This method runs in multiple threads and applies the processing function to each item in the input queue.
     * Processed items are added to the output queue maintaining the order they were added to the input queue.
     */
    private void process() {
        try {
            while (true) {
                Item<Tin> item;
                inputLock.lock();
                try {
                    while (inputQueue.isEmpty() && !isShutdown.get()) {
                        inputCondition.await(); // Wait for an item to be added to the input queue
                    }
                    if (isShutdown.get()) break;
                    item = inputQueue.poll();
                } finally {
                    inputLock.unlock();
                }
                Tout processedData = processingFunction.apply(item.data); // Apply the processing function to the item

                outputLock.lock();
                try {
                    outputQueue.add(new Item<>(item.seqNum, processedData));
                    if (callback != null && !outputQueue.isEmpty() && outputQueue.peek().seqNum == nextOutputNum.get()) {
                        callback.accept(new RetrieveResult<>(List.of(outputQueue.peek().data), outputQueue.size() - 1));
                    }
                    outputCondition.signalAll(); // Notify waiting threads that an item has been processed
                } finally {
                    outputLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Item class to hold data and its sequence number
    private static class Item<T> implements Comparable<Item<T>> {
        private final long seqNum;
        private final T data;

        /**
         * Constructs an Item object with a sequence number and data.
         *
         * @param seqNum The sequence number of this item.
         * @param data   The data of this item.
         */
        public Item(long seqNum, T data) {
            this.seqNum = seqNum;
            this.data = data;
        }

        /**
         * Compares this item with the specified item for order.
         *
         * @param other The item to be compared.
         * @return A negative integer, zero, or a positive integer as this item is less than, equal to, or greater than the specified item.
         */
        @Override
        public int compareTo(Item<T> other) {
            return Long.compare(seqNum, other.seqNum);
        }
    }

    // RetrieveResult class to hold processed data and the number of remaining items
    public static class RetrieveResult<T> {
        private final List<T> data = new ArrayList<>();
        private int remaining;

        /**
         * Constructs a RetrieveResult object with a list of data and the number of remaining items.
         *
         * @param data      The list of retrieved data.
         * @param remaining The number of remaining items.
         */
        private RetrieveResult(List<T> data, int remaining) {
            if(data != null) this.data.addAll(data);
            this.remaining = remaining;
        }

        /**
         * Gets the list of retrieved data.
         *
         * @return The list of retrieved data.
         */
        public List<T> getData() {
            return data;
        }

        /**
         * Gets the number of remaining items.
         *
         * @return The number of remaining items.
         */
        public int getRemaining() {
            return remaining;
        }
    }
}
