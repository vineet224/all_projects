package practice.lld.firstiteration.paymentgateway2chatgpt;

import java.util.*;
import java.util.concurrent.*;

/*
================================================================================
                    INTERVIEW ALIGNMENT & SCOPE CONTRACT
================================================================================
CORE SCOPE:
1. Idempotent Payment Processing (Prevent Double Spend via ConcurrentHashMap).
2. Dynamic Bank Routing based on Instrument Type (UPI -> HDFC, Card -> ICICI).
3. Async Bank Webhook Updates to manage State Machine Transitions safely.

OUT OF SCOPE FOR RUNNABILITY: Auth, PCI-DSS Encryption, Database persistence.
================================================================================
*/

enum PaymentStatus { CREATED, PENDING_CALLBACK, SUCCESS, FAILED }
enum InstrumentType { UPI, CARD }

// 1. DOMAIN ENTITIES (Minimized boilerplate using direct fields)
class Transaction {
    String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 5);
    String idempotencyKey;
    String merchantId;
    double amount;
    InstrumentType type;
    volatile PaymentStatus status = PaymentStatus.CREATED;
    String routedBank;

    public Transaction(String idempotencyKey, String merchantId, double amount, InstrumentType type) {
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.amount = amount;
        this.type = type;
    }

    // Thread-safe state transition boundary
    public synchronized void transitionTo(PaymentStatus nextStatus) {
        if (this.status == PaymentStatus.SUCCESS || this.status == PaymentStatus.FAILED) return;
        this.status = nextStatus;
    }
}

class Merchant {
    String id;
    Set<InstrumentType> allowedInstruments = new HashSet<>();

    public Merchant(String id, List<InstrumentType> instruments) {
        this.id = id;
        this.allowedInstruments.addAll(instruments);
    }
}

// 2. IN-MEMORY STORAGE LAYER
class DatabaseMock {
    public static final Map<String, Merchant> merchants = new ConcurrentHashMap<>();
    public static final Map<String, Transaction> txnsByIdempotencyKey = new ConcurrentHashMap<>();
    public static final Map<String, Transaction> txnsById = new ConcurrentHashMap<>();
}

// 3. CORE SERVICE ORCHESTRATOR
class PaymentGatewayService {

    // Core Business Rule Engine (Inline strategy instead of massive routing framework)
    private String determineBankRoute(InstrumentType type) {
        return (type == InstrumentType.UPI) ? "HDFC_BANK" : "ICICI_BANK";
    }

    public Transaction processPayment(String key, String merchantId, double amount, InstrumentType type) {
        // 1. Instant Idempotency Resolution
        if (DatabaseMock.txnsByIdempotencyKey.containsKey(key)) {
            System.out.println("[Gateway] Idempotency triggered! Returning existing transaction record.");
            return DatabaseMock.txnsByIdempotencyKey.get(key);
        }

        // 2. Core Validation
        Merchant merchant = DatabaseMock.merchants.get(merchantId);
        if (merchant == null || !merchant.allowedInstruments.contains(type)) {
            throw new IllegalArgumentException("Invalid merchant or unsupported payment method.");
        }

        // 3. Atomically claim execution slot to prevent identical concurrent race conditions
        Transaction newTxn = new Transaction(key, merchantId, amount, type);
        Transaction existing = DatabaseMock.txnsByIdempotencyKey.putIfAbsent(key, newTxn);
        if (existing != null) return existing; // Fallback context if a thread tied the race

        // 4. Map index and emulate route dispatching
        DatabaseMock.txnsById.put(newTxn.txnId, newTxn);
        newTxn.routedBank = determineBankRoute(type);
        newTxn.transitionTo(PaymentStatus.PENDING_CALLBACK);

        System.out.println("[Gateway] Forwarded to " + newTxn.routedBank + " for Transaction ID: " + newTxn.txnId);
        return newTxn;
    }

    // 4. ASYNC STATE HANDLER (Fulfills NFR Consistency requirement)
    public void handleBankCallback(String txnId, boolean isSuccess) {
        Transaction txn = DatabaseMock.txnsById.get(txnId);
        if (txn == null) return;

        txn.transitionTo(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        System.out.println("[Webhook] Status update processed. Transaction: " + txnId + " is now " + txn.status);

        // VERBAL NOTE: Outbox Pattern message would be published here to trigger merchant webhook alert.
    }
}

// 5. THE RUNNER (Stress testing concurrency to show Senior-level execution)
public class PaymentGateway {
    public static void main(String[] args) throws InterruptedException {
        PaymentGatewayService service = new PaymentGatewayService();

        // Setup Test Merchant Configuration
        DatabaseMock.merchants.put("M_SWIGGY", new Merchant("M_SWIGGY", Arrays.asList(InstrumentType.UPI, InstrumentType.CARD)));

        String commonKey = "IDEM_KEY_1111";
        ExecutorService threadPool = Executors.newFixedThreadPool(2);

        System.out.println("--- Executing Concurrent Request Stress Test ---");
        // Simulate an exact simultaneous double-click event
        threadPool.submit(() -> {
            Transaction t = service.processPayment(commonKey, "M_SWIGGY", 250.0, InstrumentType.UPI);
            System.out.println("Thread 1 returned Transaction: " + t.txnId + " with Status: " + t.status);
        });

        threadPool.submit(() -> {
            Transaction t = service.processPayment(commonKey, "M_SWIGGY", 250.0, InstrumentType.UPI);
            System.out.println("Thread 2 returned Transaction: " + t.txnId + " with Status: " + t.status);
        });

        threadPool.shutdown();
        threadPool.awaitTermination(2, TimeUnit.SECONDS);

        // Process downstream callback sequence
        Transaction activeTxn = DatabaseMock.txnsByIdempotencyKey.get(commonKey);
        System.out.println("\n--- Processing Downstream Bank Status Feedback ---");
        service.handleBankCallback(activeTxn.txnId, true);
    }
}
