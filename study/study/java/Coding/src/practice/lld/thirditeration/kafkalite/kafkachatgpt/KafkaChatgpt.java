package practice.lld.thirditeration.kafkalite.kafkachatgpt;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

record Message(String id, String payload) {}

class Topic {
    private final String name;
    private final List<Message> log = new ArrayList<>();
    private final Object lock = new Object();

    public Topic(String name) { this.name = name; }

    public void publish(Message message) {
        synchronized (lock) {
            log.add(message);
            lock.notifyAll(); // Wake up consumers waiting for new messages
        }
    }

    public List<Message> poll(int offset, int maxBatchSize, long timeoutMs) throws InterruptedException {
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (offset >= log.size()) {
                long waitTime = deadline - System.currentTimeMillis();
                if (waitTime <= 0) return Collections.emptyList();
                lock.wait(waitTime);
            }
            int toIndex = Math.min(log.size(), offset + maxBatchSize);
            return new ArrayList<>(log.subList(offset, toIndex));
        }
    }
}

class Consumer implements AutoCloseable {
    private final String id;
    private final Topic topic;
    private int offset = 0;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Consumer(String id, Topic topic) {
        this.id = id;
        this.topic = topic;
    }

    public void start() {
        executor.submit(() -> {
            while (running.get()) {
                try {
                    List<Message> batch = topic.poll(offset, 10, 500);
                    for (Message msg : batch) {
                        System.out.printf("[%s] Consumed: %s (id: %s)%n", id, msg.payload(), msg.id());
                        offset++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
    }
}