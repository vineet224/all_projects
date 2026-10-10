package practice.lld.thirditeration.cache.chatgpt;

import java.util.HashMap;
import java.util.Map;

public class LRUCache<K, V> {

    private static class Node<K, V> {
        K key;
        V value;
        long expireAt;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value, long expireAt) {
            this.key = key;
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private final int capacity;
    private final long ttlMillis;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;

    public LRUCache(int capacity, long ttlMillis) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.ttlMillis = ttlMillis;
        this.map = new HashMap<>();

        // Sentinel nodes to eliminate null checks
        this.head = new Node<>(null, null, 0);
        this.tail = new Node<>(null, null, 0);
        head.next = tail;
        tail.prev = head;
    }

    public synchronized V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }

        // Lazy TTL eviction
        if (ttlMillis > 0 && System.currentTimeMillis() > node.expireAt) {
            removeNode(node);
            map.remove(key);
            return null;
        }

        moveToHead(node);
        return node.value;
    }

    public synchronized void put(K key, V value) {
        Node<K, V> existing = map.get(key);
        long expireAt = (ttlMillis > 0) ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;

        if (existing != null) {
            existing.value = value;
            existing.expireAt = expireAt;
            moveToHead(existing);
            return;
        }

        if (map.size() >= capacity) {
            evictLRU();
        }

        Node<K, V> newNode = new Node<>(key, value, expireAt);
        map.put(key, newNode);
        addToHead(newNode);
    }

    public synchronized boolean delete(K key) {
        Node<K, V> node = map.remove(key);
        if (node != null) {
            removeNode(node);
            return true;
        }
        return false;
    }

    private void addToHead(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    private void evictLRU() {
        Node<K, V> lru = tail.prev;
        if (lru != head) {
            removeNode(lru);
            map.remove(lru.key);
        }
    }

    public static void main(String[] args) {
        LRUCache<String, Integer> cache = new LRUCache<>(2, 1000 * 60);

        cache.put("A", 1);
        cache.put("B", 2);
        System.out.println("Get A: " + cache.get("A")); // Access A -> B becomes LRU

        cache.put("C", 3); // Evicts B
        System.out.println("Get B (expected null): " + cache.get("B"));
        System.out.println("Get C: " + cache.get("C"));
        System.out.println("Get A: " + cache.get("A"));
    }
}
