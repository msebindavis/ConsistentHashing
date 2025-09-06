package main.java.sebindavis.consistanthash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;

public class ConsistentHashRingImpl2 implements ConsistentHashRing {

    private final int numberOfReplicas;
    private final List<Integer> hashOfServers = new ArrayList<>();
    private final Map<Integer, String> hashToNodeMap = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    public ConsistentHashRingImpl2(int numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
    }


    public int numberOfReplicas() {
        return numberOfReplicas;
    }

    public int getNearestServerIndex(int hash) {
        if (hashOfServers.isEmpty()) {
                return 0;
            }
        int l = 0;
        int r = hashOfServers.size() - 1;

        while(l < r){
            int m = l + (r-l)/2;

            if(hashOfServers.get(m) > hash){
                r = m;
            } else {
                l = m + 1;
            }
        }
        if (hashOfServers.get(l) <= hash) {
            return 0;  // wrap around to the first server
        }

        return l;
    }

        public int getNearestServerIndexForAddNode(int hash) {
        if (hashOfServers.isEmpty()) {
                return 0;
            }
        int l = 0;
        int r = hashOfServers.size() - 1;

        while(l < r){
            int m = l + (r-l)/2;

            if(hashOfServers.get(m) > hash){
                r = m;
            } else {
                l = m + 1;
            }
        }

        return l - 1;
    }

    public int findServer(int hash) {
        int l = 0;
        int r = hashOfServers.size() - 1;

        while(l <= r){
            int m = l + (r-l)/2;
            if(hashOfServers.get(m) == hash) return m;
            if(hashOfServers.get(m) > hash){
                r = m - 1;
            } else {
                l = m + 1;
            }
        }

        return -1;
    }

    public void addServer(int index, int hash){
        hashOfServers.add(index, hash);
    }



    public void addNode(String node) {
        writeLock.lock();
        try {
            for (int i = 0; i < numberOfReplicas; i++) {
                int hash = getHash(node + i);
                hashOfServers.add(hash);
                hashToNodeMap.put(hash, node);
            }
            Collections.sort(hashOfServers);
            }
        finally {
                writeLock.unlock();
            }
    }

    public  void removeNode(String node) {
        writeLock.lock();
         try {
        for (int i = 0; i < numberOfReplicas; i++) {
            int hash = getHash(node + i);
            int index = findServer(hash);
            hashOfServers.remove(index);
            hashToNodeMap.remove(hash);
        }
        } finally {
                writeLock.unlock();
            }
    }

    public String getNode(String key) {
        if (hashOfServers.isEmpty()) {
            return null;
        }
        int hash = getHash(key);
        readLock.lock();
        try {
            int index = getNearestServerIndex(hash);
            int hashOfNode = hashOfServers.get(index);
            return hashToNodeMap.get(hashOfNode);
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