package practice.lld.seconditeration.amazoninventoryFull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: AMAZON INVENTORY SYSTEM
 * =======================================================================================
 * * 1. FUNCTIONAL REQUIREMENTS:
 * - Add new products and update inventory stock.
 * - Reserve inventory when a user initiates checkout (multi-item support).
 * - Confirm order (deduct permanently) upon payment success.
 * - Release inventory (rollback) upon payment failure/timeout.
 * * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - High Concurrency: Prevent overselling (no negative stock).
 * - Deadlock Prevention: Multi-item checkouts must not freeze threads.
 * * * 3. ASSUMPTIONS (State these upfront):
 * - Single warehouse routing. (Multi-region logic is too complex for 45 mins).
 * - Users checkout directly with a list of items (skipping Cart management).
 * - Payment Gateway is mocked.
 * * * 4. HLD CALLOUTS (For the Interviewer):
 * - Database Optimistic Locking: "In an RDBMS, I wouldn't use application-level ReentrantLocks.
 * I'd use a versioning column (Optimistic Locking) `UPDATE inventory SET stock = stock - 1,
 * version = version + 1 WHERE id = X AND stock > 0 AND version = Y` to prevent overselling."
 * - Redis/Cache: "For flash sales, I'd move inventory counters to a Redis cluster using
 * LUA scripts for atomic decrements before hitting the main database."
 * - Outbox Pattern: "After order creation, I'd use the Transactional Outbox pattern to
 * reliably publish an 'OrderCreatedEvent' to Kafka for the shipping microservice."
 * * * 5. SKIPPED FOR TIME / PRIORITY constraints:
 * - Shopping Cart persistence, User Authentication, Shipping & Logistics tracking,
 * and pagination on product search.
 * =======================================================================================
 */

// --- 1. Enums & POJOs ---

enum OrderStatus {
    PENDING_PAYMENT, CONFIRMED, CANCELLED
}

class Product {
    private final String productId;
    private final String name;
    private final double price;

    public Product(String productId, String name, double price) {
        this.productId = productId;
        this.name = name;
        this.price = price;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public double getPrice() { return price; }
}

class Order {
    private final String orderId;
    private final String userId;
    private final Map<String, Integer> items; // ProductId -> Quantity
    private OrderStatus status;

    public Order(String orderId, String userId, Map<String, Integer> items) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = items;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public Map<String, Integer> getItems() { return items; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
}

// --- 2. Domain Models ---

class InventoryRecord {
    private final String productId;
    private int availableStock;
    private int reservedStock;
    // Granular lock strictly for this SKU/Product
    private final ReentrantLock lock;

    public InventoryRecord(String productId, int initialStock) {
        this.productId = productId;
        this.availableStock = initialStock;
        this.reservedStock = 0;
        this.lock = new ReentrantLock();
    }

    public String getProductId() { return productId; }
    public ReentrantLock getLock() { return lock; }
    public int getAvailableStock() { return availableStock; }

    public void addStock(int quantity) {
        lock.lock();
        try {
            availableStock += quantity;
        } finally {
            lock.unlock();
        }
    }

    // Move from Available -> Reserved (Holding state)
    public boolean reserve(int quantity) {
        if (availableStock >= quantity) {
            availableStock -= quantity;
            reservedStock += quantity;
            return true;
        }
        return false;
    }

    // Move from Reserved -> Dispatched (Permanent)
    public void commit(int quantity) {
        // In a real system, you'd check bounds, but here we assume strict state machine progression
        reservedStock -= quantity;
    }

    // Move from Reserved -> Available (Rollback)
    public void rollback(int quantity) {
        reservedStock -= quantity;
        availableStock += quantity;
    }
}

// --- 3. Core System ---

class InventoryManager {
    private static InventoryManager instance;
    private final Map<String, Product> productCatalog;
    private final Map<String, InventoryRecord> inventory;
    private final Map<String, Order> activeOrders;
    private final AtomicInteger orderCounter = new AtomicInteger(1000);

    private InventoryManager() {
        this.productCatalog = new ConcurrentHashMap<>();
        this.inventory = new ConcurrentHashMap<>();
        this.activeOrders = new ConcurrentHashMap<>();
    }

    public static synchronized InventoryManager getInstance() {
        if (instance == null) {
            instance = new InventoryManager();
        }
        return instance;
    }

    public void addProduct(Product product, int initialStock) {
        productCatalog.put(product.getProductId(), product);
        inventory.put(product.getProductId(), new InventoryRecord(product.getProductId(), initialStock));
    }

    public void displayInventory() {
        System.out.println("\n--- CURRENT INVENTORY ---");
        for (InventoryRecord record : inventory.values()) {
            System.out.println(productCatalog.get(record.getProductId()).getName() +
                    " | Available: " + record.getAvailableStock());
        }
        System.out.println("-------------------------\n");
    }

    /**
     * CORE LOGIC: Checking out multiple items safely.
     */
    public Order checkout(String userId, Map<String, Integer> cartItems) {
        // 1. Validate all products exist
        for (String productId : cartItems.keySet()) {
            if (!inventory.containsKey(productId)) {
                System.out.println("❌ Error: Product " + productId + " does not exist.");
                return null;
            }
        }

        // 2. DEADLOCK PREVENTION: Sort Product IDs alphabetically before locking.
        // If User1 buys [iPhone, Charger] and User2 buys [Charger, iPhone],
        // sorting ensures both threads attempt to lock Charger first, avoiding deadlocks.
        List<String> sortedProductIds = new ArrayList<>(cartItems.keySet());
        Collections.sort(sortedProductIds);

        List<InventoryRecord> lockedRecords = new ArrayList<>();
        boolean successfulReservation = true;

        try {
            // 3. Acquire all locks in sorted order
            for (String productId : sortedProductIds) {
                InventoryRecord record = inventory.get(productId);
                // Wait up to 2 seconds to acquire the lock to prevent infinite thread blocking
                if (record.getLock().tryLock(2, TimeUnit.SECONDS)) {
                    lockedRecords.add(record);
                } else {
                    successfulReservation = false;
                    System.out.println("⚠️ Timeout: Could not acquire lock for " + productId);
                    break;
                }
            }

            // 4. Verify stock availability for ALL items
            if (successfulReservation) {
                for (String productId : sortedProductIds) {
                    InventoryRecord record = inventory.get(productId);
                    int requestedQty = cartItems.get(productId);
                    if (record.getAvailableStock() < requestedQty) {
                        successfulReservation = false;
                        System.out.println("❌ Out of Stock: Cannot fulfill " + requestedQty + "x " +
                                productCatalog.get(productId).getName());
                        break; // Stop checking, we fail the whole order
                    }
                }
            }

            // 5. Reserve the stock
            if (successfulReservation) {
                for (String productId : sortedProductIds) {
                    InventoryRecord record = inventory.get(productId);
                    record.reserve(cartItems.get(productId));
                }

                // 6. Generate Order
                String orderId = "ORD-" + orderCounter.incrementAndGet();
                Order order = new Order(orderId, userId, cartItems);
                activeOrders.put(orderId, order);
                System.out.println("⏳ PENDING: Order " + orderId + " created for User " + userId + ". Awaiting payment.");
                return order;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            successfulReservation = false;
        } finally {
            // 7. CRITICAL: Always unlock in a finally block to prevent memory leaks/frozen systems
            for (InventoryRecord record : lockedRecords) {
                record.getLock().unlock();
            }
        }

        return null; // Checkout failed
    }

    public boolean confirmPayment(String orderId) {
        Order order = activeOrders.get(orderId);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) return false;

        // Since stock is ALREADY reserved, we just commit it.
        // We lock briefly to ensure memory visibility across threads.
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            InventoryRecord record = inventory.get(entry.getKey());
            record.getLock().lock();
            try {
                record.commit(entry.getValue());
            } finally {
                record.getLock().unlock();
            }
        }

        order.setStatus(OrderStatus.CONFIRMED);
        System.out.println("✅ SUCCESS: Payment confirmed. Order " + orderId + " is ready for dispatch!");
        return true;
    }

    public void handlePaymentFailure(String orderId) {
        Order order = activeOrders.get(orderId);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) return;

        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            InventoryRecord record = inventory.get(entry.getKey());
            record.getLock().lock();
            try {
                record.rollback(entry.getValue());
            } finally {
                record.getLock().unlock();
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        System.out.println("⏰ CANCELLED: Payment failed for Order " + orderId + ". Stock rolled back.");
    }
}

// --- 4. Main Execution & Concurrency Test ---

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING AMAZON INVENTORY ===\n");
        InventoryManager amazon = InventoryManager.getInstance();

        amazon.addProduct(new Product("P1", "iPhone 15", 999.00), 5);
        amazon.addProduct(new Product("P2", "MacBook Pro", 1999.00), 2);

        amazon.displayInventory();

        System.out.println("=== SCENARIO 1: FLASH SALE RACE CONDITION ===");
        // Only 2 MacBooks available. 3 Users try to buy 1 at the EXACT same time.
        ExecutorService executor = Executors.newFixedThreadPool(3);

        Runnable buyMacBook = () -> {
            String userId = "User-" + Thread.currentThread().getId();
            Map<String, Integer> cart = new HashMap<>();
            cart.put("P2", 1); // 1 MacBook

            Order order = amazon.checkout(userId, cart);
            if (order != null) {
                amazon.confirmPayment(order.getOrderId());
            }
        };

        executor.submit(buyMacBook);
        executor.submit(buyMacBook);
        executor.submit(buyMacBook); // This 3rd one MUST fail.

        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);

        System.out.println("\n=== SCENARIO 2: PAYMENT FAILURE & ROLLBACK ===");
        Map<String, Integer> aliceCart = new HashMap<>();
        aliceCart.put("P1", 2); // 2 iPhones

        Order aliceOrder = amazon.checkout("Alice", aliceCart);
        amazon.displayInventory(); // iPhones should drop from 5 to 3

        System.out.println("Alice's card is declined...");
        amazon.handlePaymentFailure(aliceOrder.getOrderId());

        amazon.displayInventory(); // iPhones should roll back to 5
    }
}