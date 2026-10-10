package practice.lld.seconditeration.sqs;

import java.util.*;
import java.util.concurrent.*;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: AWS SQS CLONE (PULL + VISIBILITY TIMEOUT)
 * =======================================================================================
 * 1. FUNCTIONAL: Consumers PULL messages. They must explicitly DELETE them after processing.
 * 2. NON-FUNCTIONAL: High concurrency pull with strict Visibility Timeouts.
 * 3. ASSUMPTIONS: Visibility timeout is 2 seconds for this simulation.
 * 4. HLD CALLOUTS: "Because SQS is HTTP-based, it cannot push. The Visibility Timeout
 * acts as a distributed lock. If a consumer JVM OOMs or crashes while processing,
 * the lock expires and another consumer node will naturally pick up the slack."
 * =======================================================================================
 */

// --- 1. Domain Models ---
class SqsMessage {
    final String messageId;
    final String body;
    String receiptHandle; // Required to delete the message
    long visibleAfterEpochMillis; // The lock timestamp

    public SqsMessage(String body) {
        this.messageId = UUID.randomUUID().toString();
        this.body = body;
        this.visibleAfterEpochMillis = 0; // Immediately visible
    }
}

// --- 2. The SQS Engine ---
class SQSQueue {
    private final String name;
    private final int visibilityTimeoutMillis;

    // We use a Queue for order, but must iterate to find the first "visible" message
    private final ConcurrentLinkedQueue<SqsMessage> messages = new ConcurrentLinkedQueue<>();

    public SQSQueue(String name, int visibilityTimeoutMillis) {
        this.name = name;
        this.visibilityTimeoutMillis = visibilityTimeoutMillis;
    }

    public void sendMessage(String body) {
        messages.offer(new SqsMessage(body));
    }

    /**
     * CORE SIGNAL: Pulling with Distributed Locking (Visibility Timeout)
     */
    public SqsMessage receiveMessage() {
        long now = System.currentTimeMillis();

        for (SqsMessage msg : messages) {
            // Lock briefly on the message to prevent race conditions between 2 consumers pulling
            synchronized (msg) {
                if (now >= msg.visibleAfterEpochMillis) {
                    // Lock the message! Hide it from others for X seconds.
                    msg.visibleAfterEpochMillis = now + visibilityTimeoutMillis;
                    msg.receiptHandle = UUID.randomUUID().toString(); // Generate new token

                    return msg; // Deliver it
                }
            }
        }
        return null; // Queue is empty, or all messages are currently locked/in-flight
    }

    /**
     * CORE SIGNAL: Explicit Deletion via Receipt Handle
     */
    public boolean deleteMessage(String messageId, String receiptHandle) {
        for (SqsMessage msg : messages) {
            if (msg.messageId.equals(messageId)) {
                synchronized (msg) {
                    if (receiptHandle.equals(msg.receiptHandle)) {
                        messages.remove(msg);
                        return true;
                    }
                }
            }
        }
        return false; // Invalid handle or already deleted
    }
}

// --- 3. Main Execution & Test ---
public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING SQS CLONE (2s Timeout) ===");
        SQSQueue sqs = new SQSQueue("Image-Processing-Queue", 2000);

        sqs.sendMessage("Process_Image_A");

        System.out.println("\n=== SCENARIO 1: THE PULL ===");
        SqsMessage pulledMsg = sqs.receiveMessage();
        System.out.println("Consumer 1 Pulled: " + pulledMsg.body);

        System.out.println("\n=== SCENARIO 2: THE IN-FLIGHT LOCK ===");
        SqsMessage emptyPull = sqs.receiveMessage();
        System.out.println("Consumer 2 Pulled: " + (emptyPull == null ? "NULL (Message is locked!)" : emptyPull.body));

        System.out.println("\n=== SCENARIO 3: VISIBILITY EXPIRATION ===");
        System.out.println("Waiting 2.5 seconds for lock to expire (simulating Consumer 1 crash)...");
        Thread.sleep(2500);

        SqsMessage rePulledMsg = sqs.receiveMessage();
        System.out.println("Consumer 2 Pulled Again: " + rePulledMsg.body);

        System.out.println("\n=== SCENARIO 4: EXPLICIT DELETION (ACK) ===");
        boolean deleted = sqs.deleteMessage(rePulledMsg.messageId, rePulledMsg.receiptHandle);
        System.out.println("Consumer 2 Deleted Message: " + deleted);

        System.out.println("Final Queue Check: " + (sqs.receiveMessage() == null ? "Queue is strictly Empty" : "Failed"));
    }
}