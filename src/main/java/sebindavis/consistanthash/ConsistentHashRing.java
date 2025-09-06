package main.java.sebindavis.consistanthash;

public interface ConsistentHashRing {
    
    /**
     * Adds a node to the consistent hash ring
     * @param node the node identifier to add
     */
    void addNode(String node);
    
    /**
     * Removes a node from the consistent hash ring
     * @param node the node identifier to remove
     */
    void removeNode(String node);
    
    /**
     * Gets the node responsible for the given key
     * @param key the key to look up
     * @return the node identifier responsible for the key, or null if no nodes exist
     */
    String getNode(String key);
    
    /**
     * Returns the number of virtual replicas per physical node
     * @return the number of replicas
     */
    int numberOfReplicas();
}