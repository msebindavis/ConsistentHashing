package sebindavis.consistenthash;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit Jupiter Test Class for ConsistentHashRing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConsistentHashRingTest {

    private ConsistentHashRing hashRing;

    @BeforeEach
    void setUp() {
        // Replace with your implementation class
        hashRing = new ConsistentHashRingImpl2(100); // 100 virtual nodes per physical node
    }

    /**
     * Test basic add/remove functionality
     */
    @Test
    @Order(1)
    void testAddAndRemoveNodes() {
        hashRing.addNode("NodeA");
        hashRing.addNode("NodeB");

        assertNotNull(hashRing.getNode("myKey1"), "Key should map to a node");
        assertTrue(Set.of("NodeA", "NodeB").contains(hashRing.getNode("myKey2")));

        hashRing.removeNode("NodeA");
        assertEquals("NodeB", hashRing.getNode("myKey1"), "Only NodeB should remain");

        hashRing.removeNode("NodeB");
        assertNull(hashRing.getNode("myKey1"), "Should return null when no nodes exist");
    }

    /**
     * Benchmark for consistent hashing performance
     */
    @Test
    @Order(2)
    void benchmarkConsistentHashRingPerformance() {
        int nodes = 50;
        int keys = 1_000_000;

        for (int i = 0; i < nodes; i++) {
            hashRing.addNode("Node-" + i);
        }

        long start = System.nanoTime();
        for (int i = 0; i < keys; i++) {
            hashRing.getNode("Key-" + i);
        }
        long duration = System.nanoTime() - start;

        double opsPerSec = keys / (duration / 1_000_000_000.0);

        System.out.printf("Benchmark: %,d lookups in %.2f seconds → %.2f ops/sec%n",
                keys, duration / 1_000_000_000.0, opsPerSec);

        assertTrue(opsPerSec > 100_000, "Throughput should be >100k ops/sec");
    }

    /**
     * Multi-threaded test to ensure thread safety
     */
    @Test
    @Order(3)
    void testConcurrentAccess() throws InterruptedException {
        int threads = 10;
        int iterations = 100_000;

        for (int i = 0; i < 10; i++) {
            hashRing.addNode("Node-" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger successfulLookups = new AtomicInteger();

        Runnable task = () -> {
            Random random = new Random();
            for (int i = 0; i < iterations; i++) {
                String key = "Key-" + random.nextInt(1_000_000);
                String node = hashRing.getNode(key);
                if (node != null) successfulLookups.incrementAndGet();

                // Occasionally add/remove a node to simulate dynamic behavior
                if (i % 1000 == 0) {
                    String newNode = "DynamicNode-" + random.nextInt(100);
                    hashRing.addNode(newNode);
                    hashRing.removeNode(newNode);
                }
            }
        };

        // Submit all threads
        IntStream.range(0, threads).forEach(i -> executor.submit(task));

        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS), "Threads did not finish in time");

        System.out.printf("Concurrent test: %,d successful lookups%n", successfulLookups.get());
        assertTrue(successfulLookups.get() > 0, "There should be successful lookups");
    }
}
