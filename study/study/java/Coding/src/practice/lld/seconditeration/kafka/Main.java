package practice.lld.seconditeration.kafka;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: KAFKA-STYLE PUB/SUB (APPEND-ONLY LOG + OFFSET PULLING)
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Producers can publish messages to specific topics.
 * - Consumers can subscribe to topics and process messages sequentially.
 * - Multiple consumers on the same topic process the same messages independently.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Concurrency: Producers appending to the log MUST NOT corrupt data.
 * - Pull-Based Efficiency: Consumers track their own offsets and MUST NOT busy-wait
 * (spin the CPU) when there are no new messages.
 * * 3. ASSUMPTIONS:
 * - Single Partition per Topic: All messages in a topic maintain strict global order.
 * - No Consumer Groups: Every registered consumer reads all messages from the topic.
 * - Unbounded Log: Messages are never deleted (no retention policy).
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Disk I/O & Zero-Copy: "In this LLD, messages are in memory. Real Kafka writes to an
 * append-only disk log and uses OS-level `sendfile` (Zero-Copy) to transfer bytes
 * directly from disk cache to the network socket, bypassing the JVM heap entirely."
 * - Partitions & Scaling: "To scale beyond a single node's throughput, I would split
 * topics into Partitions, mapping specific keys to partitions via hashing."
 * - Zookeeper/KRaft: "Kafka relies on consensus algorithms to manage broker metadata
 * and consumer offsets. Here, offsets are held in local memory by the consumer thread."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - Consumer Group rebalancing, Message TTL/Eviction, and Batch Publishing.
 * =======================================================================================
 */

// --- 1. Domain Models ---

class Message {
    final String id;
    final String payload;

    public Message(String payload) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.payload = payload;
    }

    @Override
    public String toString() { return "[" + id + "] " + payload; }
}

// --- 2. Core Engine: The Append-Only Log (Topic) ---

class Topic {
    public final String name;

    // The underlying Log. We use standard ArrayList because all accesses are protected by the lock.
    private final List<Message> log = new ArrayList<>();

    private final ReentrantLock lock = new ReentrantLock();
    // Condition allows consumers to sleep when they reach the end of the log
    private final Condition newMessageCondition = lock.newCondition();

    public Topic(String name) {
        this.name = name;
    }

    /**
     * PRODUCER API: Append message to the log and wake up sleeping consumers.
     */
    public void publish(Message message) {
        lock.lock();
        try {
            log.add(message);
            // Signal all consumers waiting for new messages to wake up and pull
            newMessageCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * CONSUMER API: Fetch a message at a specific offset.
     * If offset hasn't been written yet, the thread sleeps (Zero busy-waiting).
     */
    public Message consume(int offset) throws InterruptedException {
        lock.lock();
        try {
            // While the requested offset doesn't exist, go to sleep.
            while (offset >= log.size()) {
                newMessageCondition.await();
            }
            return log.get(offset);
        } finally {
            lock.unlock();
        }
    }
}

// --- 3. The Pull-Based Consumer ---

class ConsumerWorker implements Runnable {
    private final String consumerId;
    private final Topic topic;
    private int currentOffset;
    private volatile boolean isRunning;

    public ConsumerWorker(String consumerId, Topic topic) {
        this.consumerId = consumerId;
        this.topic = topic;
        this.currentOffset = 0; // Independent offset tracking
        this.isRunning = true;
    }

    @Override
    public void run() {
        System.out.println("🚀 Consumer [" + consumerId + "] started pulling from " + topic.name);
        while (isRunning) {
            try {
                // PULL MODEL: Consumer asks for a specific offset.
                // This call blocks (sleeps) if the offset is not yet available.
                Message msg = topic.consume(currentOffset);

                System.out.println("✅ [" + consumerId + "] processed " + msg.toString());

                // Advance offset only AFTER successful processing
                currentOffset++;

                // Simulate processing time
                Thread.sleep(100);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("🛑 Consumer [" + consumerId + "] stopped.");
    }

    public void shutdown() {
        isRunning = false;
    }
}

// --- 4. The Broker (Manager) ---

class MessageBroker {
    private final Map<String, Topic> topics = new ConcurrentHashMap<>();

    public void createTopic(String name) {
        topics.putIfAbsent(name, new Topic(name));
        System.out.println("🛠️ Created Topic: " + name);
    }

    public Topic getTopic(String name) {
        return topics.get(name);
    }

    public void publish(String topicName, String payload) {
        Topic topic = topics.get(topicName);
        if (topic != null) {
            topic.publish(new Message(payload));
        }
    }
}

// --- 5. Main Execution & Concurrency Test ---

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING MINI-KAFKA ===\n");
        MessageBroker broker = new MessageBroker();
        broker.createTopic("ORDERS");

        Topic ordersTopic = broker.getTopic("ORDERS");

        // Create two independent consumers on the same topic.
        // Because they track their own offsets, they will BOTH receive ALL messages independently.
        ConsumerWorker analyticsService = new ConsumerWorker("Analytics-Service", ordersTopic);
        ConsumerWorker shippingService = new ConsumerWorker("Shipping-Service", ordersTopic);

        Thread t1 = new Thread(analyticsService);
        Thread t2 = new Thread(shippingService);

        t1.start();
        t2.start();

        // Give consumers a moment to boot and go to sleep (awaiting messages)
        Thread.sleep(500);

        System.out.println("\n=== PRODUCER PUBLISHING MESSAGES ===");
        broker.publish("ORDERS", "Order_1001_Created");
        Thread.sleep(200); // Slight delay to watch consumers react

        broker.publish("ORDERS", "Order_1002_Created");
        Thread.sleep(500);

        System.out.println("\n=== LATE SUBSCRIBER REPLAY (The Kafka Superpower) ===");
        // A massive benefit of Kafka is that late consumers can replay the entire log from offset 0
        ConsumerWorker auditService = new ConsumerWorker("Audit-Service (Late)", ordersTopic);
        Thread t3 = new Thread(auditService);
        t3.start();

        // Audit service will instantly pull Order 1001 and 1002 from memory,
        // while Analytics and Shipping remain parked at offset 2.

        Thread.sleep(1000);

        // Cleanup
        analyticsService.shutdown();
        shippingService.shutdown();
        auditService.shutdown();

        // Interrupt threads to break them out of `await()` so they can terminate cleanly
        t1.interrupt();
        t2.interrupt();
        t3.interrupt();
    }
}
