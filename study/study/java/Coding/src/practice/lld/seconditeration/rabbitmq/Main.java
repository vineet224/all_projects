package practice.lld.seconditeration.rabbitmq;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: RABBITMQ CLONE (PUSH MODEL + ROUND ROBIN)
 * =======================================================================================
 * 1. FUNCTIONAL: Producers publish to an Exchange/Queue. Broker PUSHES to subscribers.
 * 2. NON-FUNCTIONAL: Competing Consumers (Load balancing messages across multiple threads).
 * 3. ASSUMPTIONS: Auto-ACK enabled (message is deleted the moment it's pushed for simplicity).
 * 4. HLD CALLOUTS: "In real RabbitMQ, we use Manual ACKs. The broker keeps the message
 * in an 'Unacked' state until the consumer replies. If the consumer disconnects, the
 * broker re-queues it."
 * =======================================================================================
 */

// --- 1. Consumer Interface (The Callback) ---
interface MessageListener {
    void onMessage(String message);
}

// --- 2. The Smart Queue ---
class PushQueue {
    public final String name;
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final List<MessageListener> consumers = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private volatile boolean isRunning = true;

    public PushQueue(String name) {
        this.name = name;
        startPushWorker();
    }

    public void addConsumer(MessageListener listener) {
        consumers.add(listener);
    }

    public void publish(String message) {
        messages.offer(message);
    }

    // CORE SIGNAL: The Push Worker
    private void startPushWorker() {
        Thread worker = new Thread(() -> {
            while (isRunning) {
                try {
                    // Pulls from internal queue. Blocks if empty to save CPU.
                    String msg = messages.take();

                    if (consumers.isEmpty()) {
                        // Dead letter or re-queue logic could go here.
                        // For now, put it back and sleep briefly.
                        messages.offer(msg);
                        Thread.sleep(100);
                        continue;
                    }

                    // Round-Robin Load Balancing
                    int index = Math.abs(roundRobinIndex.getAndIncrement() % consumers.size());
                    MessageListener consumer = consumers.get(index);

                    // PUSH to the consumer
                    CompletableFuture.runAsync(() -> consumer.onMessage(msg));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }
}

// --- 3. Main Execution & Test ---
public class  Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING RABBITMQ CLONE ===");
        PushQueue taskQueue = new PushQueue("Task-Queue");

        // Competing Consumers: Both listen to the same queue.
        taskQueue.addConsumer(msg -> {
            System.out.println("👷 Worker 1 Processing: " + msg);
            try { Thread.sleep(50); } catch (Exception e){} // Simulate work
        });

        taskQueue.addConsumer(msg -> {
            System.out.println("👷 Worker 2 Processing: " + msg);
        });

        System.out.println("=== PUBLISHING MESSAGES ===");
        // Messages will be distributed round-robin, removing them from the queue instantly.
        for (int i = 1; i <= 5; i++) {
            taskQueue.publish("Job_" + i);
        }

        Thread.sleep(1000); // Allow async pushes to complete
    }
}