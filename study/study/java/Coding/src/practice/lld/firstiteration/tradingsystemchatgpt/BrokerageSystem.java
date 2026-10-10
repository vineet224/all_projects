package practice.lld.firstiteration.tradingsystemchatgpt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

class UserWallet {
    private final String userId;
    private double balance;
    private double heldFunds;
    private final ReentrantLock lock = new ReentrantLock();

    public UserWallet(String userId, double initialBalance) {
        this.userId = userId;
        this.balance = initialBalance;
        this.heldFunds = 0;
    }

    // 1. Lock funds before sending to exchange
    public boolean holdFunds(double amount) {
        lock.lock();
        try {
            if (balance >= amount) {
                balance -= amount;
                heldFunds += amount;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    // 2a. Exchange succeeded: funds are gone forever
    public void commitFunds(double amount) {
        lock.lock();
        try {
            heldFunds -= amount;
        } finally {
            lock.unlock();
        }
    }

    // 2b. Exchange failed: return funds to available balance
    public void rollbackFunds(double amount) {
        lock.lock();
        try {
            heldFunds -= amount;
            balance += amount;
        } finally {
            lock.unlock();
        }
    }

    public double getAvailableBalance() { return balance; }
}

class OrderRequest {
    String idempotencyKey;
    String userId;
    int quantity;
    double maxPrice;
    double lockedAmount; // Track how much we locked for this specific order

    public OrderRequest(String key, String user, int qty, double price) {
        this.idempotencyKey = key;
        this.userId = user;
        this.quantity = qty;
        this.maxPrice = price;
        this.lockedAmount = qty * price;
    }
}

public class BrokerageSystem {
    private final Map<String, UserWallet> wallets = new ConcurrentHashMap<>();
    private final Map<String, OrderRequest> pendingOrders = new ConcurrentHashMap<>();

    public void createWallet(String userId, double initialBalance) {
        wallets.putIfAbsent(userId, new UserWallet(userId, initialBalance));
    }

    // Step 1: User places the order
    public boolean placeBuyOrder(OrderRequest request) {
        // Idempotency: prevent double-charging if client retries
        if (pendingOrders.putIfAbsent(request.idempotencyKey, request) != null) {
            System.out.println("Duplicate order request: " + request.idempotencyKey);
            return true;
        }

        UserWallet wallet = wallets.get(request.userId);

        // Try to lock funds
        if (!wallet.holdFunds(request.lockedAmount)) {
            pendingOrders.remove(request.idempotencyKey); // Free up the key
            System.out.println("Insufficient funds for user: " + request.userId);
            return false;
        }

        System.out.println("Funds held. Routing order " + request.idempotencyKey + " to Exchange...");
        // In a real system, we make an async API call to Jane Street/NSE here.
        return true;
    }

    // Step 2: The Exchange calls us back via Webhook
    public void exchangeCallback(String idempotencyKey, boolean isSuccess) {
        OrderRequest request = pendingOrders.remove(idempotencyKey);
        if (request == null) return; // Order already processed or doesn't exist

        UserWallet wallet = wallets.get(request.userId);

        if (isSuccess) {
            wallet.commitFunds(request.lockedAmount);
            System.out.println("Order " + idempotencyKey + " SUCCESS. Funds committed.");
        } else {
            wallet.rollbackFunds(request.lockedAmount);
            System.out.println("Order " + idempotencyKey + " FAILED. Funds refunded.");
        }
    }

    // Quick test to prove it works
    public static void main(String[] args) {
        BrokerageSystem broker = new BrokerageSystem();
        broker.createWallet("user_vineet", 5000.0);

        OrderRequest order1 = new OrderRequest("uuid-1", "user_vineet", 10, 100.0); // Costs $1000
        broker.placeBuyOrder(order1);

        // Simulate Exchange succeeding
        broker.exchangeCallback("uuid-1", true);

        OrderRequest order2 = new OrderRequest("uuid-2", "user_vineet", 100, 100.0); // Costs $10000 (Should fail)
        broker.placeBuyOrder(order2);
    }
}