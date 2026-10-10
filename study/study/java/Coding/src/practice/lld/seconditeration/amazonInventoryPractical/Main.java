package practice.lld.seconditeration.amazonInventoryPractical;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD: AMAZON INVENTORY SYSTEM (LEAN 45-MIN VERSION)
 * =======================================================================================
 * Do NOT build a Shopping Cart: A cart is just a temporary Redis hash in the real world. Skip it. Assume the user clicks "Checkout" and sends a Map of ProductIDs and Quantities directly to your Order Service.
 *
 *
 * 1. FUNCTIONAL: Checkout multiple items, handle payment failure (rollback).
 * 2. NON-FUNCTIONAL: High Concurrency (prevent overselling), Deadlock Prevention.
 * 3. ASSUMPTIONS: No shopping cart, direct checkout with Product IDs and Quantities.
 * 4. HLD CALLOUTS: "I'm using ReentrantLocks here. In a DB, I'd use Optimistic Locking
 * (versioning) or a Redis LUA script for atomic decrements to prevent overselling."
 * 5. CUT FOR TIME: Product metadata (name, price), User classes, and complex routing.
 * =======================================================================================
 */

enum OrderStatus { PENDING, CONFIRMED, CANCELLED }

class Order {
    final String orderId;
    final Map<String, Integer> items;
    OrderStatus status = OrderStatus.PENDING;

    Order(String orderId, Map<String, Integer> items) {
        this.orderId = orderId;
        this.items = items;
    }
}

class InventoryRecord {
    int stock;
    final ReentrantLock lock = new ReentrantLock();

    InventoryRecord(int stock) { this.stock = stock; }
}

class InventoryManager {
    private final Map<String, InventoryRecord> inventory = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger orderCounter = new AtomicInteger(100);

    public void addStock(String productId, int stock) {
        inventory.put(productId, new InventoryRecord(stock));
    }

    /**
     * CORE SIGNAL: Deadlock Prevention and Atomic Decrement
     */
    public Order checkout(Map<String, Integer> cart) {
        // 1. DEADLOCK PREVENTION: Sort product IDs before acquiring locks
        List<String> sortedIds = new ArrayList<>(cart.keySet());
        Collections.sort(sortedIds);

        List<InventoryRecord> lockedRecords = new ArrayList<>();
        boolean success = true;

        try {
            // 2. ACQUIRE LOCKS
            for (String pId : sortedIds) {
                InventoryRecord record = inventory.get(pId);
                if (record == null) return null; // Invalid product

                if (record.lock.tryLock(2, TimeUnit.SECONDS)) {
                    lockedRecords.add(record);
                } else {
                    success = false;
                    break;
                }
            }

            // 3. CHECK STOCK (Fail fast if any item is out of stock)
            if (success) {
                for (String pId : sortedIds) {
                    if (inventory.get(pId).stock < cart.get(pId)) {
                        success = false;
                        break;
                    }
                }
            }

            // 4. DEDUCT STOCK & CREATE ORDER
            if (success) {
                for (String pId : sortedIds) {
                    inventory.get(pId).stock -= cart.get(pId);
                }
                Order order = new Order("ORD-" + orderCounter.incrementAndGet(), cart);
                orders.put(order.orderId, order);
                System.out.println("✅ Checkout Success: " + order.orderId);
                return order;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            success = false;
        } finally {
            // 5. CRITICAL: Always release locks
            for (InventoryRecord record : lockedRecords) {
                record.lock.unlock();
            }
        }
        System.out.println("❌ Checkout Failed (Stock/Lock Issue).");
        return null;
    }

    public void paymentFailed(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || order.status != OrderStatus.PENDING) return;

        // Rollback: Re-add stock
        for (Map.Entry<String, Integer> item : order.items.entrySet()) {
            InventoryRecord record = inventory.get(item.getKey());
            record.lock.lock();
            try {
                record.stock += item.getValue();
            } finally {
                record.lock.unlock();
            }
        }
        order.status = OrderStatus.CANCELLED;
        System.out.println("🔄 Payment Failed. Stock Rolled Back for: " + orderId);
    }
}

// --- Main Execution ---
public class Main {
    public static void main(String[] args) throws InterruptedException {
        InventoryManager amazon = new InventoryManager();
        amazon.addStock("IPHONE", 2);
        amazon.addStock("MACBOOK", 1);

        System.out.println("=== RACE CONDITION TEST ===");
        // 3 Users try to buy 1 MacBook simultaneously. Only 1 should succeed.
        ExecutorService executor = Executors.newFixedThreadPool(3);
        Runnable buyMacbook = () -> {
            Map<String, Integer> cart = new HashMap<>();
            cart.put("MACBOOK", 1);
            amazon.checkout(cart);
        };

        executor.submit(buyMacbook);
        executor.submit(buyMacbook);
        executor.submit(buyMacbook);

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("\n=== ROLLBACK TEST ===");
        Map<String, Integer> cart2 = new HashMap<>();
        cart2.put("IPHONE", 2);
        Order order = amazon.checkout(cart2);

        amazon.paymentFailed(order.orderId); // Puts the 2 iPhones back

        // This should now succeed because stock was rolled back
        amazon.checkout(cart2);
    }
}
