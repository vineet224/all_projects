package practice.lld.seconditeration.stocktradebroker;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: STOCK TRADING BROKER (WALLET & EXCHANGE MANAGEMENT)
 * =======================================================================================
 *
 *
 * The core trap candidates fall into is wasting 30 minutes trying to implement a complex match-order book. Don't do it. The prompt explicitly says "Broker only" and "interface NSE". The broker’s job is to:
 *
 * Verify and lock the user's wallet balance (so they don't overspend).
 *
 * Forward the order to the NSE interface.
 *
 * Handle the response (Commit the wallet deduction on success, or Rollback on failure).
 *
 * The Time Sinks (What to cut)
 * Real Order Matching Engine: The NSE handles the matching. We mock the NSE interface entirely.
 *
 * Complex Portfolios: Tracking average buy price, P&L, etc., is pure boilerplate. Cut it to fit the 45-minute window.
 *
 *
 * 1. FUNCTIONAL REQUIREMENT:
 * - Place a BUY order: Validate/lock user wallet balance, route to NSE, handle response.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Thread-Safety: Balance updates must be atomic. A user cannot double-spend their wallet.
 * - Resilience: If NSE fails, crashes, or rejects, user funds must be instantly restored.
 * * 3. ASSUMPTIONS:
 * - We are implementing BUY orders only for the 45-min scope (SELL is symmetric).
 * - NSE interface represents an external system; its network calls are mocked.
 * - Wallet balances are managed in-memory using granular locking per user.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Distributed Transactions: "Since the Broker DB and NSE are separate systems, I would
 * use the Saga Pattern (Orchestration) or a 2-Phase Commit simulation to guarantee
 * eventual consistency across systems."
 * - Idempotency: "Every order routed to the NSE would carry a unique Idempotency Key. If a
 * network timeout occurs, retries won't result in duplicate stock purchases."
 * - Write Bottleneck: "For real-time balances under high trading volume, I'd use Redis for
 * fast ledger checks and stream transactions to a DB via Kafka asynchronously."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - Portfolio/Holdings tracking, Market vs Limit order types (assumed Market),
 * and authentication/kyc handling.
 * =======================================================================================
 */

// --- 1. Core Interfaces & Enums ---

enum OrderStatus { PENDING, SUCCESS, FAILED }

interface NSE {
    // Simulates external exchange execution. Returns true if order executes successfully.
    boolean executeOrder(String orderId, String symbol, int qty, double price);
}

// --- 2. Domain Models ---

class UserWallet {
    private final String userId;
    private double balance;
    // Granular lock per user wallet to maximize concurrency across different users
    final ReentrantLock lock = new ReentrantLock();

    public UserWallet(String userId, double initialBalance) {
        this.userId = userId;
        this.balance = initialBalance;
    }

    public double getBalance() { return balance; }

    public boolean reserveFunds(double amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }

    public void refundFunds(double amount) {
        balance += amount;
    }
}

class Order {
    final String orderId;
    final String userId;
    final String symbol;
    final int qty;
    final double price;
    OrderStatus status = OrderStatus.PENDING;

    public Order(String orderId, String userId, String symbol, int qty, double price) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.qty = qty;
        this.price = price;
    }
}

// --- 3. Core System (Broker Manager) ---

class TradingBroker {
    private final Map<String, UserWallet> wallets = new ConcurrentHashMap<>();
    private final Map<String, Order> orderBook = new ConcurrentHashMap<>();
    private final AtomicInteger orderIdGenerator = new AtomicInteger(1000);
    private final NSE nseExchange;

    public TradingBroker(NSE nseExchange) {
        this.nseExchange = nseExchange;
    }

    public void createUserWallet(String userId, double initialBalance) {
        wallets.put(userId, new UserWallet(userId, initialBalance));
    }

    public double getBalance(String userId) {
        return wallets.get(userId).getBalance();
    }

    /**
     * CORE SIGNAL: Wallet reservation, Exchange routing, and Transaction Rollback
     */
    public Order placeBuyOrder(String userId, String symbol, int qty, double estimatedPrice) {
        UserWallet wallet = wallets.get(userId);
        if (wallet == null) throw new IllegalArgumentException("User not found");

        double totalRequiredFunds = qty * estimatedPrice;
        String orderId = "ORD-" + orderIdGenerator.incrementAndGet();
        Order order = new Order(orderId, userId, symbol, qty, estimatedPrice);
        orderBook.put(orderId, order);

        // Phase 1: Lock Wallet and Reserve Funds Locally
        wallet.lock.lock();
        boolean fundsReserved = false;
        try {
            fundsReserved = wallet.reserveFunds(totalRequiredFunds);
        } finally {
            wallet.lock.unlock(); // Release early! Do NOT hold lock during external API network calls.
        }

        if (!fundsReserved) {
            order.status = OrderStatus.FAILED;
            System.out.println("❌ Order " + orderId + " Failed: Insufficient Wallet Balance.");
            return order;
        }

        // Phase 2: Route to External Exchange (NSE)
        System.out.println("⏳ Funds reserved. Routing Order " + orderId + " to NSE...");
        boolean exchangeSuccess = nseExchange.executeOrder(orderId, symbol, qty, estimatedPrice);

        // Phase 3: Commit or Rollback based on Exchange response
        if (exchangeSuccess) {
            order.status = OrderStatus.SUCCESS;
            System.out.println("✅ Order " + orderId + " successfully executed on NSE.");
        } else {
            // Rollback Phase: Exchange rejected or timed out, refund the wallet
            order.status = OrderStatus.FAILED;
            wallet.lock.lock();
            try {
                wallet.refundFunds(totalRequiredFunds);
            } finally {
                wallet.lock.unlock();
            }
            System.out.println("🔄 NSE execution failed. Refunded ₹" + totalRequiredFunds + " to user " + userId);
        }

        return order;
    }
}

// --- 4. Mock Exchange & Main Execution ---

class MockNSE implements NSE {
    @Override
    public boolean executeOrder(String orderId, String symbol, int qty, double price) {
        // Mocking a failure scenario for specific test cases
        if ("REJECT_ME".equals(symbol)) {
            return false;
        }
        return true; // Assume standard processing success
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        NSE nse = new MockNSE();
        TradingBroker broker = new TradingBroker(nse);

        broker.createUserWallet("USER_A", 10000.00); // Start with ₹10,000

        System.out.println("=== TEST 1: SUCCESSFUL ORDER PLACE ===");
        // Buy 10 shares of RELIANCE at ₹500 = ₹5,000 cost. Should succeed.
        broker.placeBuyOrder("USER_A", "RELIANCE", 10, 500.00);
        System.out.println("User A Final Balance: ₹" + broker.getBalance("USER_A")); // Should be ₹5,000

        System.out.println("\n=== TEST 2: EXCHANGE REJECTION & WALLET ROLLBACK ===");
        // Try buying a stock that the exchange will reject. Fund rollback must trigger.
        broker.placeBuyOrder("USER_A", "REJECT_ME", 2, 1000.00);
        System.out.println("User A Balance Post-Rejection: ₹" + broker.getBalance("USER_A")); // Must stay ₹5,000 (refunded)

        System.out.println("\n=== TEST 3: CONCURRENT ACCOUNT EXHAUSTION ===");
        // Reset wallet to ₹1,500
        broker.createUserWallet("USER_B", 1500.00);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Spin up 2 simultaneous threads trying to buy ₹1,000 worth of stock. Only one can win.
        Runnable parallelOrder = () -> broker.placeBuyOrder("USER_B", "TATA", 1, 1000.00);

        executor.submit(parallelOrder);
        executor.submit(parallelOrder);

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("User B Final Balance: ₹" + broker.getBalance("USER_B")); // Must be exactly ₹500, not negative!
    }
}