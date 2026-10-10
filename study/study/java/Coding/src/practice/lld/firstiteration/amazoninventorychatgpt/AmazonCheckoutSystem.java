package practice.lld.firstiteration.amazoninventorychatgpt;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

// 1. Core Models
enum OrderStatus {
    PENDING_PAYMENT, SUCCESS, FAILED
}

class Stock {
    int total;
    int reserved;

    public Stock(int total) {
        this.total = total;
        this.reserved = 0;
    }

    public int getAvailable() {
        return total - reserved;
    }
}

class Order {
    final String orderId;
    final Map<String, Integer> items;
    OrderStatus status;
    final long expiresAt;

    public Order(String orderId, Map<String, Integer> items, long ttlMillis) {
        this.orderId = orderId;
        this.items = items;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.expiresAt = System.currentTimeMillis() + ttlMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}

// 2. The Core Engine
class InventoryManager {
    private final ConcurrentHashMap<String, Stock> inventory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> productLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Order> orderRepository = new ConcurrentHashMap<>();

    private final ScheduledExecutorService sweeperService = Executors.newSingleThreadScheduledExecutor();

    public InventoryManager() {
        // Start the background TTL Sweeper for abandoned carts
        sweeperService.scheduleAtFixedRate(this::sweepAbandonedCarts, 1, 1, TimeUnit.SECONDS);
    }

    public void addProduct(String productId, int initialStock) {
        inventory.put(productId, new Stock(initialStock));
        productLocks.putIfAbsent(productId, new ReentrantLock());
    }

    // --- SENIOR ABSTRACTION: Reusable Lock Manager ---
    private List<ReentrantLock> acquireLocks(Set<String> productIds) {
        List<String> sortedIds = new ArrayList<>(productIds);
        Collections.sort(sortedIds); // Prevent Deadlocks

        List<ReentrantLock> acquired = new ArrayList<>();
        for (String pId : sortedIds) {
            ReentrantLock lock = productLocks.get(pId);
            if (lock != null) {
                lock.lock();
                acquired.add(lock);
            }
        }
        return acquired;
    }

    private void releaseLocks(List<ReentrantLock> acquiredLocks) {
        // Release in reverse order of acquisition
        for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
            acquiredLocks.get(i).unlock();
        }
    }

    // --- PHASE 1: Checkout (Soft Allocation) ---
    public Order processCheckout(String orderId, Map<String, Integer> cart) {
        // 1. ATOMIC IDEMPOTENCY BARRIER
        // We insert a placeholder/pending order immediately.
        // If it returns non-null, another thread beat us to it.
        Order placeholder = new Order(orderId, cart, 3000); // Status is PENDING_PAYMENT
        Order existingOrder = orderRepository.putIfAbsent(orderId, placeholder);

        if (existingOrder != null) {
            System.out.println("[IDEMPOTENCY] Order " + orderId + " already processed.");
            return existingOrder;
        }

        // Now we are 100% guaranteed to be the ONLY thread processing this orderId
        List<ReentrantLock> locks = acquireLocks(cart.keySet());
        try {
            // Verification Phase
            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                Stock stock = inventory.get(entry.getKey());
                if (stock == null || stock.getAvailable() < entry.getValue()) {
                    // Rollback the idempotency placeholder if checkout fails!
                    orderRepository.remove(orderId);
                    return null;
                }
            }

            // Soft Allocation Phase
            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                inventory.get(entry.getKey()).reserved += entry.getValue();
            }

            return placeholder; // The object is already in the map, so just return it.

        } finally {
            releaseLocks(locks);
        }
    }

    // --- PHASE 2: Payment Webhook (Hard Allocation) ---
    public void handlePaymentSuccess(String orderId) {
        Order order = orderRepository.get(orderId);
        if (order == null) return;

        // Synchronize on the order to prevent race conditions with the Sweeper thread
        synchronized (order) {
            if (order.status != OrderStatus.PENDING_PAYMENT) return;

            List<ReentrantLock> locks = acquireLocks(order.items.keySet());
            try {
                for (Map.Entry<String, Integer> entry : order.items.entrySet()) {
                    Stock stock = inventory.get(entry.getKey());
                    stock.total -= entry.getValue();    // Hard deduct
                    stock.reserved -= entry.getValue(); // Free up the reservation
                }
                order.status = OrderStatus.SUCCESS;
                System.out.println("[PAYMENT SUCCESS] Order " + orderId + " fulfilled. Inventory permanently deducted.");
            } finally {
                releaseLocks(locks);
            }
        }
    }

    // --- PHASE 3: Payment Failure / TTL Expiry (Rollback) ---
    public void handlePaymentFailure(String orderId) {
        Order order = orderRepository.get(orderId);
        if (order == null) return;

        synchronized (order) {
            if (order.status != OrderStatus.PENDING_PAYMENT) return;

            List<ReentrantLock> locks = acquireLocks(order.items.keySet());
            try {
                for (Map.Entry<String, Integer> entry : order.items.entrySet()) {
                    Stock stock = inventory.get(entry.getKey());
                    stock.reserved -= entry.getValue(); // Rollback: Free up the reservation
                }
                order.status = OrderStatus.FAILED;
                System.out.println("[PAYMENT FAILED / EXPIRED] Order " + orderId + " rolled back. Reservations cleared.");
            } finally {
                releaseLocks(locks);
            }
        }
    }

    // --- BACKGROUND TASK: The Sweeper ---
    private void sweepAbandonedCarts() {
        for (Order order : orderRepository.values()) {
            if (order.status == OrderStatus.PENDING_PAYMENT && order.isExpired()) {
                System.out.println("[SWEEPER] Found abandoned cart: " + order.orderId);
                handlePaymentFailure(order.orderId);
            }
        }
    }

    public void printInventory() {
        System.out.println("\n--- Final Inventory State ---");
        for (Map.Entry<String, Stock> entry : inventory.entrySet()) {
            System.out.println(entry.getKey() + " -> Total: " + entry.getValue().total +
                    ", Reserved: " + entry.getValue().reserved +
                    ", Available: " + entry.getValue().getAvailable());
        }
    }

    public void shutdown() {
        sweeperService.shutdown();
    }
}

// 3. The Driver (Proves all 1.5-hour requirements work)
public class AmazonCheckoutSystem {
    public static void main(String[] args) throws InterruptedException {
        InventoryManager manager = new InventoryManager();

        manager.addProduct("MACBOOK", 10);
        manager.addProduct("IPHONE", 5);

        // Scenario 1: Happy Path (Checkout + Payment Success)
        Map<String, Integer> cart1 = new HashMap<>();
        cart1.put("MACBOOK", 2);
        cart1.put("IPHONE", 1);

        // Scenario 2: Abandoned Cart (Checkout + User closes browser)
        Map<String, Integer> cart2 = new HashMap<>();
        cart2.put("MACBOOK", 1);
        cart2.put("IPHONE", 4); // Takes the rest of the iPhones

        // Run concurrently
        Thread user1 = new Thread(() -> {
            manager.processCheckout("ORDER_001", cart1);
            manager.handlePaymentSuccess("ORDER_001"); // Pays immediately
        });

        Thread user2 = new Thread(() -> {
            manager.processCheckout("ORDER_002", cart2);
            // User 2 DOES NOT PAY. Let the sweeper handle it.
        });

        Thread user3 = new Thread(() -> {
            // Idempotency Test: User 1 retries clicking checkout due to network lag
            manager.processCheckout("ORDER_001", cart1);
        });

        user1.start();
        user2.start();

        // Wait a beat before user 3 sends the duplicate request
        Thread.sleep(500);
        user3.start();

        user1.join();
        user2.join();
        user3.join();

        System.out.println("\nWaiting for Sweeper to clean up abandoned Order 2...");
        Thread.sleep(4000); // Wait for the 3-second TTL to expire

        manager.printInventory();
        manager.shutdown();
    }
}