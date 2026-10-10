package practice.lld.seconditeration.noitificationSystem;

import java.util.*;
import java.util.concurrent.*;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: NOTIFICATION SYSTEM (ASYNC + OBSERVER PATTERN)
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Send notifications via multiple channels (Email, SMS, Push).
 * - Support different notification types (Transactional, Promotional).
 * - Respect user preferences (e.g., User opted out of Promotional SMS).
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Asynchronous: Sending a notification MUST NOT block the calling application thread.
 * - Extensibility (OCP): Adding a new channel (e.g., WhatsApp) requires zero changes to core.
 * * 3. ASSUMPTIONS:
 * - User preferences are pre-loaded into memory for the scope of the interviews.interview.
 * - Fire-and-forget simulation: We assume the external gateway (Twilio/SendGrid) succeeds.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Message Queue (Kafka/RabbitMQ): "In production, I wouldn't use an in-memory Executor.
 * The API would push the event to Kafka, and a separate Notification Worker fleet would
 * consume it. This ensures we don't lose notifications if the JVM crashes."
 * - Dead Letter Queue (DLQ) & Retries: "If Twilio is down, the worker catches the exception
 * and pushes the message to a DLQ with exponential backoff for retries."
 * - Rate Limiting & Deduplication: "I'd use a Redis Token Bucket to ensure we don't spam a
 * user with 50 SMS messages a minute, and a Redis idempotency key to prevent duplicate sends."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - String Templating (e.g., replacing {{name}}), Retry/DLQ implementation, and
 * Bulk/Batch broadcast routing.
 * =======================================================================================
 */

// --- 1. Enums & Domain Models ---

enum Channel { EMAIL, SMS, PUSH }

enum NotificationType {
    TRANSACTIONAL, // e.g., OTP, Order Confirmation (Usually bypasses strict opt-outs)
    PROMOTIONAL    // e.g., Sales, Discounts (Requires explicit opt-in)
}

class User {
    final String userId;
    final String email;
    final String phone;
    // Maps NotificationType -> Set of approved Channels
    final Map<NotificationType, Set<Channel>> preferences;

    public User(String userId, String email, String phone) {
        this.userId = userId;
        this.email = email;
        this.phone = phone;
        this.preferences = new HashMap<>();
    }

    public void setPreference(NotificationType type, Set<Channel> channels) {
        preferences.put(type, channels);
    }
}

// --- 2. The Strategy Pattern: Channel Senders ---

interface NotificationSender {
    Channel getChannel();
    void send(User user, String message);
}

class EmailSender implements NotificationSender {
    @Override public Channel getChannel() { return Channel.EMAIL; }
    @Override public void send(User user, String message) {
        System.out.println("📧 [EMAIL -> " + user.email + "] : " + message);
    }
}

class SmsSender implements NotificationSender {
    @Override public Channel getChannel() { return Channel.SMS; }
    @Override public void send(User user, String message) {
        System.out.println("📱 [SMS -> " + user.phone + "] : " + message);
    }
}

// --- 3. Core Engine: Notification Dispatcher ---

class NotificationService {
    // Registry of available senders (OCP Compliant)
    private final Map<Channel, NotificationSender> senderRegistry = new HashMap<>();

    // CORE SIGNAL: Async execution to prevent blocking the main app thread
    private final ExecutorService executorService;

    public NotificationService(int threadPoolSize) {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void registerSender(NotificationSender sender) {
        senderRegistry.put(sender.getChannel(), sender);
    }

    /**
     * CORE SIGNAL: Preference Routing & Async Dispatch
     */
    public void dispatch(User user, NotificationType type, String message) {
        // 1. Fetch User Preferences for this specific type
        Set<Channel> approvedChannels = user.preferences.getOrDefault(type, Collections.emptySet());

        if (approvedChannels.isEmpty()) {
            System.out.println("🔇 User " + user.userId + " has opted out of " + type + " notifications.");
            return;
        }

        // 2. Dispatch asynchronously to all approved channels
        for (Channel channel : approvedChannels) {
            NotificationSender sender = senderRegistry.get(channel);

            if (sender != null) {
                executorService.submit(() -> {
                    try {
                        // Simulate network latency
                        Thread.sleep(100);
                        sender.send(user, "[" + type + "] " + message);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } else {
                System.err.println("⚠️ No sender registered for channel: " + channel);
            }
        }
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}

// --- 4. Main Execution & Test ---

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING NOTIFICATION SYSTEM ===\n");
        NotificationService service = new NotificationService(5);

        // Registering channels dynamically
        service.registerSender(new EmailSender());
        service.registerSender(new SmsSender());
        // Notice: If we wanted to add WhatsApp, we just create WhatsAppSender and register it.
        // The NotificationService class NEVER has to change. (Perfect OCP).

        // 1. Setup User & Preferences
        User alice = new User("U001", "alice@domain.com", "+1234567890");

        // Alice wants Transactional alerts on BOTH Email and SMS
        alice.setPreference(NotificationType.TRANSACTIONAL, EnumSet.of(Channel.EMAIL, Channel.SMS));

        // Alice only wants Promotional alerts on Email (Opted out of SMS spam)
        alice.setPreference(NotificationType.PROMOTIONAL, EnumSet.of(Channel.EMAIL));

        System.out.println("=== SCENARIO 1: TRANSACTIONAL NOTIFICATION ===");
        // The main thread returns instantly. The printing happens in background threads.
        service.dispatch(alice, NotificationType.TRANSACTIONAL, "Your OTP is 492011.");

        System.out.println("=== SCENARIO 2: PROMOTIONAL NOTIFICATION ===");
        // Should only trigger Email, SMS should be suppressed by preference.
        service.dispatch(alice, NotificationType.PROMOTIONAL, "50% OFF on all items!");

        System.out.println("=== SCENARIO 3: OPT-OUT / SILENCED ===");
        User bob = new User("U002", "bob@domain.com", "+0987654321");
        // Bob has no preferences set (default empty)
        service.dispatch(bob, NotificationType.PROMOTIONAL, "Buy one get one free!");

        // Allow async threads to finish printing before shutting down
        Thread.sleep(500);
        service.shutdown();
        System.out.println("\nSystem Shut Down.");
    }
}