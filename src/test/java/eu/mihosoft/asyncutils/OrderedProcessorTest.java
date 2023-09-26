package eu.mihosoft.asyncutils;

import eu.mihosoft.asyncutils.OrderedProcessor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OrderedProcessorTests class is responsible for testing the OrderedProcessor functionalities.
 * It has different test methods to test adding, retrieving data in order, out of order and concurrently.
 */
class OrderedProcessorTests {

    /**
     * Mockup Processing Function.
     * This function will simulate a scenario where the processing occurs out of order
     * by sleeping for a varying amount of time for each input.
     * @param input An integer representing the item to be processed.
     * @return The same integer, representing the processed item.
     */
    private int processFunction(Integer input) {
        try {
            // Making the thread sleep to simulate processing time
            Thread.sleep(100 * (5 - input)); // Process out of order
        } catch (InterruptedException e) {
            // Handling interruption
            Thread.currentThread().interrupt();
        }
        return input;
    }

    /**
     * Tests the scenario where data is added and retrieved in order.
     * Validates the order of the data retrieved is as expected.
     */
    @Test
    void addRetrieveInOrder() {
        // Creating OrderedProcessor object with processFunction as the processing function
        OrderedProcessor<Integer, Integer> processor = new OrderedProcessor<>(2, this::processFunction, null);

        // Adding data in order
        for (int i = 0; i < 5; ++i)
            processor.addData(i);

        // Retrieving the data
        var result = processor.retrieveData(5, false);

        // Validating the size and remaining data
        assertEquals(5, result.getData().size());
        assertEquals(0, result.getRemaining());

        // Asserting the order of retrieved data is as expected
        for (int i = 0; i < 5; ++i)
            assertEquals(i, result.getData().get(i));
    }

    /**
     * Tests the scenario where data is added and retrieved out of order.
     * Validates the order of the data retrieved is as expected.
     */
    @Test
    void addRetrieveOutOfOrder() throws InterruptedException {
        // Creating OrderedProcessor object with processFunction as the processing function
        OrderedProcessor<Integer, Integer> processor = new OrderedProcessor<>(2, this::processFunction, null);

        // Adding data in order
        for (int i = 0; i < 5; ++i)
            processor.addData(i);

        // Allowing time for the processing function to process data out of order
        Thread.sleep(1000);

        // Retrieving the data
        var result = processor.retrieveData(5, false);

        // Validating the size and remaining data
        assertEquals(5, result.getData().size());
        assertEquals(0, result.getRemaining());

        // Asserting the order of retrieved data is as expected
        for (int i = 0; i < 5; ++i)
            assertEquals(i, result.getData().get(i));
    }

    /**
     * Tests the scenario where data is added and retrieved concurrently.
     * Validates the order of the data retrieved is as expected.
     */
    @Test
    void concurrentAddRetrieve() throws InterruptedException {
        // Creating OrderedProcessor object with processFunction as the processing function
        OrderedProcessor<Integer, Integer> processor = new OrderedProcessor<>(2, this::processFunction, null);

        // Initializing CountDownLatch to ensure all threads are completed before assertion
        CountDownLatch latch = new CountDownLatch(2);

        // Spawning a thread to add data concurrently
        ExecutorService addingService = Executors.newSingleThreadExecutor();
        addingService.submit(() -> {
            try {
                for (int i = 0; i < 5; ++i) {
                    processor.addData(i);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                // Handling interruption
                Thread.currentThread().interrupt();
            } finally {
                // Decrementing the latch count after adding data
                latch.countDown();
            }
        });

        // Allowing some time for data to be added before starting the retrieval
        Thread.sleep(250);

        // Spawning a thread to retrieve data concurrently
        ExecutorService retrievingService = Executors.newSingleThreadExecutor();
        retrievingService.submit(() -> {
            try {
                var result = processor.retrieveData(5, false);
                // Validating the size and remaining data
                assertEquals(5, result.getData().size());
                assertEquals(0, result.getRemaining());

                // Asserting the order of retrieved data is as expected
                for (int i = 0; i < 5; ++i)
                    assertEquals(i, result.getData().get(i));
            } finally {
                // Decrementing the latch count after retrieving data
                latch.countDown();
            }
        });

        // Waiting for both adding and retrieving threads to complete
        latch.await();
        // Shutting down the ExecutorServices
        addingService.shutdown();
        retrievingService.shutdown();
    }
}