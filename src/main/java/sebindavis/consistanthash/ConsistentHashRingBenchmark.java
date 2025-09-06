package main.java.sebindavis.consistanthash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class ConsistentHashRingBenchmark {

    private final ConsistentHashRing hashRing;
    private final Random random = new Random(42); // Fixed seed for reproducibility
    private final List<String> testKeys = new ArrayList<>();
    private final List<String> serverNodes = new ArrayList<>();

    public ConsistentHashRingBenchmark(int numberOfReplicas, int numberOfServers, int numberOfKeys) {
        this.hashRing = new ConsistentHashRingImpl1(numberOfReplicas);
        initializeTestData(numberOfServers, numberOfKeys);
    }

    private void initializeTestData(int numberOfServers, int numberOfKeys) {
        // Create server nodes
        for (int i = 0; i < numberOfServers; i++) {
            String server = "server-" + i;
            serverNodes.add(server);
            hashRing.addNode(server);
        }

        // Create test keys
        for (int i = 0; i < numberOfKeys; i++) {
            testKeys.add("key-" + i + "-" + random.nextInt(1000000));
        }
    }

    public void runBenchmark() {
        System.out.println("=== Consistent Hash Ring Benchmark ===");
        System.out.println("Servers: " + serverNodes.size());
        System.out.println("Replicas per server: " + hashRing.numberOfReplicas());
        System.out.println("Test keys: " + testKeys.size());
        System.out.println();

        benchmarkLookupPerformance();
        System.out.println();
        
        benchmarkAddNodePerformance();
        System.out.println();
        
        benchmarkRemoveNodePerformance();
        System.out.println();
        
        benchmarkConcurrentPerformance();
    }

    private void benchmarkLookupPerformance() {
        System.out.println("1. Lookup Performance Benchmark:");
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            hashRing.getNode(testKeys.get(i % testKeys.size()));
        }

        long startTime = System.nanoTime();
        
        int iterations = 100000;
        for (int i = 0; i < iterations; i++) {
            String key = testKeys.get(i % testKeys.size());
            hashRing.getNode(key);
        }
        
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        
        double avgTimePerLookup = (double) totalTime / iterations;
        double throughput = iterations / (totalTime / 1_000_000_000.0);
        
        System.out.printf("Total lookups: %,d%n", iterations);
        System.out.printf("Total time: %,d ns (%.3f ms)%n", totalTime, totalTime / 1_000_000.0);
        System.out.printf("Average time per lookup: %.3f ns%n", avgTimePerLookup);
        System.out.printf("Throughput: %,.0f lookups/second%n", throughput);
    }

    private void benchmarkAddNodePerformance() {
        System.out.println("2. Add Node Performance Benchmark:");
        
        String newNode = "new-server-" + System.currentTimeMillis();
        
        long startTime = System.nanoTime();
        hashRing.addNode(newNode);
        long endTime = System.nanoTime();
        
        long addTime = endTime - startTime;
        System.out.printf("Time to add node: %,d ns (%.3f ms)%n", addTime, addTime / 1_000_000.0);
        
        // Verify the node was added
        String testKey = "verification-key";
        hashRing.getNode(testKey); // This should work without errors
    }

    private void benchmarkRemoveNodePerformance() {
        System.out.println("3. Remove Node Performance Benchmark:");
        
        if (serverNodes.isEmpty()) {
            System.out.println("No servers to remove");
            return;
        }
        
        String nodeToRemove = serverNodes.get(0);
        
        long startTime = System.nanoTime();
        hashRing.removeNode(nodeToRemove);
        long endTime = System.nanoTime();
        
        long removeTime = endTime - startTime;
        System.out.printf("Time to remove node: %,d ns (%.3f ms)%n", removeTime, removeTime / 1_000_000.0);
        
        // Add it back for subsequent tests
        hashRing.addNode(nodeToRemove);
    }

    private void benchmarkConcurrentPerformance() {
        System.out.println("4. Concurrent Performance Benchmark:");
        
        int threadCount = 4;
        int operationsPerThread = 25000;
        
        List<Thread> threads = new ArrayList<>();
        long startTime = System.nanoTime();
        
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = testKeys.get(random.nextInt(testKeys.size()));
                    hashRing.getNode(key);
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        int totalOperations = threadCount * operationsPerThread;
        
        double throughput = totalOperations / (totalTime / 1_000_000_000.0);
        
        System.out.printf("Threads: %d%n", threadCount);
        System.out.printf("Operations per thread: %,d%n", operationsPerThread);
        System.out.printf("Total operations: %,d%n", totalOperations);
        System.out.printf("Total time: %,d ns (%.3f ms)%n", totalTime, totalTime / 1_000_000.0);
        System.out.printf("Throughput: %,.0f operations/second%n", throughput);
    }

    public void runDistributionTest() {
        System.out.println("5. Key Distribution Analysis:");
        
        Map<String, Integer> distribution = new HashMap<>();
        
        // Count keys per server
        for (String key : testKeys) {
            String server = hashRing.getNode(key);
            distribution.put(server, distribution.getOrDefault(server, 0) + 1);
        }
        
        System.out.println("Keys per server:");
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            double percentage = (entry.getValue() * 100.0) / testKeys.size();
            System.out.printf("  %s: %,d keys (%.1f%%)%n", 
                entry.getKey(), entry.getValue(), percentage);
        }
        
        // Calculate standard deviation
        double mean = testKeys.size() / (double) distribution.size();
        double variance = 0;
        for (int count : distribution.values()) {
            variance += Math.pow(count - mean, 2);
        }
        variance /= distribution.size();
        double stdDev = Math.sqrt(variance);
        
        System.out.printf("Distribution quality:%.1f%% (lower is better)%n", 
            (stdDev / mean) * 100);
    }

    // Main method to run the benchmark
    public static void main(String[] args) {
        int replicas = 100;          // Number of virtual nodes per physical node
        int servers = 10;            // Number of physical servers
        int testKeys = 100000;       // Number of test keys
        
        ConsistentHashRingBenchmark benchmark = 
            new ConsistentHashRingBenchmark(replicas, servers, testKeys);
        
        benchmark.runBenchmark();
        System.out.println();
        benchmark.runDistributionTest();
        
        // Run with different configurations
        System.out.println("\n=== Additional Configuration Tests ===");
        
        // Test with different replica counts
        testDifferentReplicaCounts();
    }
    
    private static void testDifferentReplicaCounts() {
        int[] replicaCounts = {10, 50, 100, 200};
        int servers = 5;
        int testKeys = 50000;
        
        for (int replicas : replicaCounts) {
            System.out.printf("\nTesting with %d replicas:%n", replicas);
            
            ConsistentHashRingBenchmark benchmark = 
                new ConsistentHashRingBenchmark(replicas, servers, testKeys);
            
            // Quick performance test
            long startTime = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                benchmark.hashRing.getNode("test-key-" + i);
            }
            long endTime = System.nanoTime();
            
            double avgTime = (endTime - startTime) / 10000.0;
            System.out.printf("Average lookup time: %.2f ns%n", avgTime);
        }
    }
}