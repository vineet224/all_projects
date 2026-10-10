package practice.lld.thirditeration.cache;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * functional requirements
 * - there should be key value pair, create, update, delete and an evication policy
 */

class ValueObject{
    int value;
    long expireepoch;
    Node reference;
    public ValueObject(int value, long expireepoch,Node reference){this.reference = reference;this.value=value;this.expireepoch=expireepoch;}
}

class Node{
    int val;
    Node next;
    Node prev;
}


public class LRUCache {

    ConcurrentHashMap<String,ValueObject> cacheStore =  new ConcurrentHashMap<String,ValueObject>();
    Node head = new Node();

    public void put(String key, int value){
        long ttl = System.currentTimeMillis() + (1000*60);
        if(cacheStore.containsKey(key)){
            ValueObject v = cacheStore.get(key);
            v.value=value;
            v.expireepoch=ttl;

            Node thisNode = v.reference;
            if(thisNode == head) {
                return;
            }
            moveToFront(thisNode);
        }else{
            Node newNode = new Node();
            newNode.val = value;
            newNode.next = head;
            head.prev = newNode;
            head = newNode;
            ValueObject v = new ValueObject(value,ttl,newNode);
            cacheStore.put(key, v);
        }
    }

    public int get(String key){
        if(!cacheStore.containsKey(key)){
            throw new IllegalArgumentException("Key not found");
        }
        ValueObject v = cacheStore.get(key);
        Node thisNode = v.reference;
        if(thisNode == head){
            return v.value;
        }
        moveToFront(thisNode);
        return v.value;
    }

    private void moveToFront(Node thisNode){
        Node prev = thisNode.prev;
        Node next = thisNode.next;
        prev.next = next;
        if(next!=null) next.prev = prev;
        thisNode.prev = null;
        thisNode.next = head;
        head.prev = thisNode;
        head =  thisNode;
    }

    public static void main(String[] args){

        LRUCache lruCache = new LRUCache();
        lruCache.put("1", 1);
        lruCache.put("2", 2);
        lruCache.put("3", 3);
        System.out.println("getting node 1 "+lruCache.get("1"));
        System.out.println("getting node 2 "+lruCache.get("2"));
    }
}
