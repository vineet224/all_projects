package practice.lld.thirditeration.kafkalite.rabbitmq;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

record Message(String id, String body) {}

interface Consumer {
    String getId();
    // Returns true for ACK, false for NACK
    boolean onMessage(Message message);
}

class PushQueue {
    private final String name;
    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
    private final List<Consumer> consumers = new CopyOnWriteArrayList<>();
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor();
    private final ExecutorService workerPool = Executors.newCachedThreadPool();
    private final AtomicInteger roundRobinIdx = new AtomicInteger(0);
    private volatile boolean active = true;

    public PushQueue(String name) {
        this.name = name;
        startDispatchLoop();
    }

    public void publish(Message message) {
        queue.offer(message);
    }

    public void registerConsumer(Consumer consumer) {
        consumers.add(consumer);
    }

    private void startDispatchLoop() {
        dispatcher.submit(() -> {
            while (active) {
                try {
                    // 1. Block until a message arrives (Zero busy-waiting, no Thread.sleep)
                    Message msg = queue.take();

                    // 2. Wait until at least one consumer is registered
                    if (consumers.isEmpty()) {
                        queue.offer(msg);
                        Thread.sleep(100);
                        continue;
                    }

                    // 3. Pick consumer via Round Robin
                    int idx = Math.abs(roundRobinIdx.getAndIncrement() % consumers.size());
                    Consumer target = consumers.get(idx);

                    // 4. Push asynchronously to worker pool so dispatcher doesn't block
                    workerPool.submit(() -> {
                        boolean ack = false;
                        try {
                            ack = target.onMessage(msg);
                        } catch (Exception e) {
                            ack = false;
                        }

                        if (!ack) {
                            System.out.printf("[%s] NACK received for %s - Requeueing%n", name, msg.id());
                            queue.offer(msg); // Requeue on failure (At-least-once delivery)
                        } else {
                            System.out.printf("[%s] ACK received for %s%n", name, msg.id());
                        }
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void shutdown() {
        active = false;
        dispatcher.shutdownNow();
        workerPool.shutdown();
    }
}

public class RabbitMQLiteRunnable {
    public static void main(String[] args) throws InterruptedException {
        PushQueue ordersQueue = new PushQueue("orders-queue");

        // Worker 1: Successful consumer
        ordersQueue.registerConsumer(new Consumer() {
            public String getId() { return "Worker-1"; }
            public boolean onMessage(Message msg) {
                System.out.printf("[%s] Processed: %s%n", getId(), msg.body());
                return true; // ACK
            }
        });

        // Worker 2: Failing consumer (Simulates transient error / NACK)
        ordersQueue.registerConsumer(new Consumer() {
            private boolean failedOnce = false;
            public String getId() { return "Worker-2"; }
            public boolean onMessage(Message msg) {
                if (!failedOnce && msg.id().equals("msg-2")) {
                    failedOnce = true;
                    System.out.printf("[%s] Failed to process: %s%n", getId(), msg.id());
                    return false; // NACK -> Requeues
                }
                System.out.printf("[%s] Processed: %s%n", getId(), msg.body());
                return true; // ACK
            }
        });

        // Publish test messages
        ordersQueue.publish(new Message("msg-1", "Create Invoice"));
        ordersQueue.publish(new Message("msg-2", "Send Welcome Email"));
        ordersQueue.publish(new Message("msg-3", "Charge Card"));

        Thread.sleep(1000);
        ordersQueue.shutdown();
    }
}