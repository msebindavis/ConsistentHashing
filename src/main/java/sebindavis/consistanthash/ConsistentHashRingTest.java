package main.java.sebindavis.consistanthash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsistentHashRingTest {

    public static void main(String[] args) {
        System.out.println("=== Consistent Hash Ring Test Report ===\n");

        // Test Configuration
        int numberOfPhysicalNodes = 50;
        int numberOfVirtualNodes = 5; // Start with a modest number for testing
        int numberOfTestKeys = 100000;
        String nodeToRemove = "Node-3";

        // Initialize the Ring
        ConsistentHashRing ring = new ConsistentHashRing(numberOfVirtualNodes);

        // 1. Test: Add Nodes and Show Distribution
        System.out.println("1. Testing Initial Node Addition and Key Distribution");
        for (int i = 0; i < numberOfPhysicalNodes; i++) {
            ring.addNode("Node-" + i);
        }

        // Generate Test Keys
        List<String> testKeys = generateTestKeys(numberOfTestKeys);
        Map<String, Integer> nodeLoadBeforeRemoval = mapKeysToNodes(ring, testKeys);

        printDistributionReport("Initial Key Distribution", nodeLoadBeforeRemoval, numberOfTestKeys);

        // 2. Test: Minimal Remapping on Node Removal
        System.out.println("\n2. Testing Minimal Removal after Removing " + nodeToRemove);
        // Remember which key was on which node BEFORE removal
        Map<String, String> keyToNodeMap = new HashMap<>();
        for (String key : testKeys) {
            keyToNodeMap.put(key, ring.getNode(key));
        }

        ring.removeNode(nodeToRemove);

        // Now check which keys moved
        int remappedKeys = 0;
        Map<String, Integer> nodeLoadAfterRemoval = new HashMap<>();

        for (String key : testKeys) {
            String currentNode = ring.getNode(key);
            String originalNode = keyToNodeMap.get(key);

            // Count the load for the new distribution
            nodeLoadAfterRemoval.put(currentNode, nodeLoadAfterRemoval.getOrDefault(currentNode, 0) + 1);

            // Check if the key moved
            if (!currentNode.equals(originalNode)) {
                remappedKeys++;
            }
        }

        double remappingPercentage = (double) remappedKeys / numberOfTestKeys * 100;
        double expectedPercentage = (1.0 / numberOfPhysicalNodes) * 100; // ~20%

        printDistributionReport("Distribution After Removal of " + nodeToRemove, nodeLoadAfterRemoval, numberOfTestKeys);
        System.out.printf("Keys Remapped: %d / %d (%.2f%%)\n", remappedKeys, numberOfTestKeys, remappingPercentage);
        System.out.printf("Theoretical Ideal Remapping: %.2f%%\n", expectedPercentage);
        System.out.printf("Verdict: %s (Remapping is close to the theoretical minimum)\n",
                (Math.abs(remappingPercentage - expectedPercentage) < 5) ? "PASS" : "FAIL");

        // 3. Test: Empty Ring and Single Node
        System.out.println("\n3. Testing Edge Cases");
        ConsistentHashRing emptyRing = new ConsistentHashRing(10);
        String result = emptyRing.getNode("test-key");
        System.out.println("Getting node from empty ring: " + result + " (Should be null)");

        ConsistentHashRing singleNodeRing = new ConsistentHashRing(10);
        singleNodeRing.addNode("Lonely-Node");
        String singleResult = singleNodeRing.getNode("test-key");
        System.out.println("Getting node from single-node ring: " + singleResult + " (Should be 'Lonely-Node')");


                // 4. Test: Multithreaded Consistency Check
        System.out.println("\n4. Testing Multithreaded Consistency");

        int numberOfPhysicalNodesMT = 20;
        int numberOfVirtualNodesMT = 50;
        int threadCount = 8;
        int keysPerThread = 100000;

        ConsistentHashRing concurrentRing = new ConsistentHashRing(numberOfVirtualNodesMT);

        // Add physical nodes
        for (int i = 0; i < numberOfPhysicalNodesMT; i++) {
            concurrentRing.addNode("Node-" + i);
        }

        List<Thread> threads = new ArrayList<>();
        Map<String, Integer> finalCounts = new HashMap<>();

        Runnable task = () -> {
            for (int i = 0; i < keysPerThread; i++) {
                String key = Thread.currentThread().getName() + "-key-" + i;
                String node = concurrentRing.getNode(key);
                synchronized (finalCounts) {
                    finalCounts.put(node, finalCounts.getOrDefault(node, 0) + 1);
                }
            }
        };

        // Start threads
        for (int t = 0; t < threadCount; t++) {
            Thread worker = new Thread(task, "Worker-" + t);
            threads.add(worker);
            worker.start();
        }

        // Wait for all threads to finish
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        printDistributionReport(
            "Multithreaded Key Distribution (" + numberOfPhysicalNodesMT + " Physical, " 
            + numberOfVirtualNodesMT + " Virtual)", 
            finalCounts, 
            threadCount * keysPerThread
        );

                // Verify correctness
        int totalAssignedKeys = finalCounts.values().stream().mapToInt(Integer::intValue).sum();
        int expectedKeys = threadCount * keysPerThread;

        System.out.println("\n--- Multithreaded Consistency Check ---");
        System.out.println("Expected total keys: " + expectedKeys);
        System.out.println("Actual total keys:   " + totalAssignedKeys);

        if (totalAssignedKeys != expectedKeys) {
            System.out.println("❌ FAIL: Key count mismatch (some keys lost or duplicated)");
        } else {
            System.out.println("✅ PASS: All keys correctly assigned");
        }

        // Calculate standard deviation
        double avg = (double) totalAssignedKeys / numberOfPhysicalNodesMT;
        double variance = finalCounts.values().stream()
                .mapToDouble(v -> Math.pow(v - avg, 2))
                .sum() / numberOfPhysicalNodesMT;
        double stddev = Math.sqrt(variance);

        System.out.printf("Standard Deviation: %.2f (Lower is better)%n", stddev);

        if (stddev < avg * 0.10) { // less than 10% imbalance
            System.out.println("✅ PASS: Distribution is balanced");
        } else {
            System.out.println("⚠️  WARN: Distribution is skewed");
        }


                // 5. Test: Concurrent Add/Remove + Lookups
        System.out.println("\n5. Testing Node Churn Under Multithreaded Load");

        int numberOfPhysicalNodesChurn = 100;
        int numberOfVirtualNodesChurn = 10;
        int churnThreads = 2;     // threads adding/removing nodes
        int readerThreads = 6;    // threads doing lookups
        int opsPerThread = 10000;

        ConsistentHashRing churnRing = new ConsistentHashRing(numberOfVirtualNodesChurn);

        // Add initial nodes
        for (int i = 0; i < numberOfPhysicalNodesChurn; i++) {
            churnRing.addNode("Node-" + i);
        }

        List<Thread> churnWorkers = new ArrayList<>();
        Map<String, Integer> churnResults = new ConcurrentHashMap<>();
        AtomicInteger nullMappings = new AtomicInteger(0);

        // Readers: continuously lookup keys
        Runnable readerTask = () -> {
            for (int i = 0; i < opsPerThread; i++) {
                String key = Thread.currentThread().getName() + "-key-" + i;
                String node = churnRing.getNode(key);
                if (node == null) {
                    nullMappings.incrementAndGet();
                } else {
                    churnResults.merge(node, 1, Integer::sum);
                }
            }
        };

        // Writers: randomly add/remove nodes
        Runnable churnTask = () -> {
            Random rand = new Random();
            for (int i = 0; i < opsPerThread; i++) {
                String nodeName = "Dynamic-" + rand.nextInt(20);
                if (rand.nextBoolean()) {
                    churnRing.addNode(nodeName);
                } else {
                    churnRing.removeNode(nodeName);
                }
            }
        };

        // Start reader threads
        for (int t = 0; t < readerThreads; t++) {
            Thread reader = new Thread(readerTask, "Reader-" + t);
            churnWorkers.add(reader);
            reader.start();
        }

        // Start churn threads
        for (int t = 0; t < churnThreads; t++) {
            Thread churner = new Thread(churnTask, "Churner-" + t);
            churnWorkers.add(churner);
            churner.start();
        }

        // Wait for all to finish
        for (Thread thread : churnWorkers) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Report
        int totalLookups = churnResults.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("\n--- Node Churn Test Results ---");
        System.out.println("Total lookups performed: " + (readerThreads * opsPerThread));
        System.out.println("Successful mappings:     " + totalLookups);
        System.out.println("Null mappings:           " + nullMappings.get());

        if (nullMappings.get() == 0) {
            System.out.println("✅ PASS: No lost mappings even under churn");
        } else {
            System.out.println("❌ FAIL: Some keys mapped to null under churn");
        }



    }

    private static List<String> generateTestKeys(int count) {
        // Generate realistic keys (e.g., user data, product IDs, session tokens)
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add("user-" + i + "-profile-data");
        }
        return keys;
    }

    private static Map<String, Integer> mapKeysToNodes(ConsistentHashRing ring, List<String> keys) {
        Map<String, Integer> loadMap = new HashMap<>();
        for (String key : keys) {
            String node = ring.getNode(key);
            loadMap.put(node, loadMap.getOrDefault(node, 0) + 1);
        }
        return loadMap;
    }

    private static void printDistributionReport(String title, Map<String, Integer> distribution, int totalKeys) {
        System.out.println("\n--- " + title + " ---");
        System.out.printf("%-10s | %-8s | %-10s\n", "Node", "Keys", "Percent");
        System.out.println("----------------------------------");
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            double percent = (double) entry.getValue() / totalKeys * 100;
            System.out.printf("%-10s | %-8d | %-10.2f%%\n", entry.getKey(), entry.getValue(), percent);
        }

        // Calculate and print standard deviation
        double mean = totalKeys / (double) distribution.size();
        double variance = 0;
        for (int count : distribution.values()) {
            variance += Math.pow(count - mean, 2);
        }
        variance /= distribution.size();
        double stdDev = Math.sqrt(variance);

        System.out.printf("\nStandard Deviation: %.2f (Lower is better)\n", stdDev);
    }
}