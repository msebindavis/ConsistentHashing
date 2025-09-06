package main.java.sebindavis.consistanthash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;

public class ConsistentHashRingImpl1 implements ConsistentHashRing {

    private final int numberOfReplicas;
    private final SortedMap<Integer, String> circle = new TreeMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    public ConsistentHashRingImpl1(int numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
    }

    @Override
    public int numberOfReplicas()  {
        return numberOfReplicas;
    }

    @Override
    public void addNode(String node) {
        writeLock.lock();
        try {
            for (int i = 0; i < numberOfReplicas; i++) {
                int hash = getHash(node + i);
                circle.put(hash, node);
            }
            }
        finally {
                writeLock.unlock();
            }
    }

    @Override
    public  void removeNode(String node) {
        writeLock.lock();
         try {
        for (int i = 0; i < numberOfReplicas; i++) {
            int hash = getHash(node + i);
            circle.remove(hash);
        }
        } finally {
                writeLock.unlock();
            }
    }

    @Override
    public String getNode(String key) {
        if (circle.isEmpty()) {
            return null;
        }
        int hash = getHash(key);
        readLock.lock();
        try {
            SortedMap<Integer, String> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            return circle.get(hash);
        } finally {
            readLock.unlock();
        }
    }

    private int getHash(String key) {
    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(key.getBytes());
        int h = ((digest[0] & 0xFF) << 24)
              | ((digest[1] & 0xFF) << 16)
              | ((digest[2] & 0xFF) << 8)
              |  (digest[3] & 0xFF);
        return h & 0x7fffffff; // keep it positive
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
    }
}

}