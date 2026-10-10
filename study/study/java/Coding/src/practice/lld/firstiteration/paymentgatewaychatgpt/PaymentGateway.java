package practice.lld.firstiteration.paymentgatewaychatgpt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
================================================================================
                    INTERVIEW ALIGNMENT & SCOPE CONTRACT
================================================================================
FUNCTIONAL REQUIREMENTS (TARGETED):
1. Onboard Merchants & Configure Instrument Support (e.g., Merchant A supports UPI, Card).
2. Process Payment Request (Idempotent execution, dynamic bank routing).
3. Handle Async Bank Webhooks (State transition management).
4. Fetch Payment Status/History for a Merchant.

EXPLICITLY OUT OF SCOPE (For Machine Coding Runnability):
- Actual Network Calls / Encryption / PCI-DSS compliance (Mocked via Bank Adapters).
- UI/Console Polling or complex query filters.
- Real Distributed Database (Simulated via Thread-Safe In-Memory Repositories).

NON-FUNCTIONAL REQUIREMENTS & ASSUMPTIONS:
- High Consistency & Concurrency: Multi-threaded requests with the same Idempotency Key
  must evaluate atomically to prevent double-spending.
- Extensibility: Adding a new Bank/Acquirer should not break existing processing logic (Open/Closed Principle).
================================================================================
*/

// ================================================================================
// 1. DOMAIN MODELS & ENUMS
// ================================================================================
enum PaymentStatus {
    CREATED,
    PENDING_BANK_CALLBACK,
    SUCCESS,
    FAILED
}

enum PaymentInstrumentType {
    UPI,
    CREDIT_CARD,
    NET_BANKING
}

// Marker interface for polymorphic payment execution credentials
interface PaymentDetails {}

class UpiDetails implements PaymentDetails {
    private final String vpa;
    public UpiDetails(String vpa) { this.vpa = vpa; }
    public String getVpa() { return vpa; }
}

class CardDetails implements PaymentDetails {
    private final String cardNumber;
    private final String cvv;
    public CardDetails(String cardNumber, String cvv) {
        this.cardNumber = cardNumber;
        this.cvv = cvv;
    }
}

// Immutable Request Context Object passed by the Client/Merchant
class PaymentRequest {
    private final String idempotencyKey;
    private final String merchantId;
    private final double amount;
    private final PaymentInstrumentType instrumentType;
    private final PaymentDetails details;

    public PaymentRequest(String idempotencyKey, String merchantId, double amount,
                          PaymentInstrumentType instrumentType, PaymentDetails details) {
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.amount = amount;
        this.instrumentType = instrumentType;
        this.details = details;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMerchantId() { return merchantId; }
    public double getAmount() { return amount; }
    public PaymentInstrumentType getInstrumentType() { return instrumentType; }
    public PaymentDetails getDetails() { return details; }
}

// Core Entity controlling Lifecycle State
class Transaction {
    private final String transactionId;
    private final String idempotencyKey;
    private final String merchantId;
    private final double amount;
    private final PaymentInstrumentType instrumentType;
    private volatile PaymentStatus status; // volatile for safe visibility across threads
    private String gatewayBankName;

    public Transaction(String idempotencyKey, String merchantId, double amount, PaymentInstrumentType instrumentType) {
        this.transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.amount = amount;
        this.instrumentType = instrumentType;
        this.status = PaymentStatus.CREATED;
    }

    // Synchronized state machine transition to block corrupted state changes
    public synchronized void transitionStatus(PaymentStatus nextStatus) {
        if (this.status == PaymentStatus.SUCCESS || this.status == PaymentStatus.FAILED) {
            return; // Terminal states cannot change
        }
        this.status = nextStatus;
    }

    public String getTransactionId() { return transactionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMerchantId() { return merchantId; }
    public double getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public void setGatewayBankName(String bankName) { this.gatewayBankName = bankName; }
    public String getGatewayBankName() { return gatewayBankName; }
}

class Merchant {
    private final String merchantId;
    private final String name;
    private final List<PaymentInstrumentType> supportedInstruments;

    public Merchant(String merchantId, String name) {
        this.merchantId = merchantId;
        this.name = name;
        this.supportedInstruments = new ArrayList<>();
    }

    public void enableInstrument(PaymentInstrumentType instrument) {
        supportedInstruments.add(instrument);
    }

    public boolean supports(PaymentInstrumentType instrument) {
        return supportedInstruments.contains(instrument);
    }
    public String getMerchantId() { return merchantId; }
}


// ================================================================================
// 2. DATA STORAGE LAYER (THREAD-SAFE REPOSITORIES)
// ================================================================================
class StorageRegistry {
    public static final Map<String, Merchant> merchants = new ConcurrentHashMap<>();
    public static final Map<String, Transaction> transactionsByIdempotencyKey = new ConcurrentHashMap<>();
    public static final Map<String, Transaction> transactionsById = new ConcurrentHashMap<>();
}


// ================================================================================
// 3. EXTERNAL BANK INTEGRATIONS (STRATEGY & ADAPTER PATTERNS)
// ================================================================================
interface BankAdapter {
    boolean process(Transaction txn, PaymentDetails details);
    String getBankName();
}

class HdfcBankAdapter implements BankAdapter {
    @Override
    public boolean process(Transaction txn, PaymentDetails details) {
        System.out.println("[HDFC Bank] Outbound processing for transaction: " + txn.getTransactionId());
        return true; // Mocks instant handshake success
    }
    @Override
    public String getBankName() { return "HDFC_BANK"; }
}

class IciciBankAdapter implements BankAdapter {
    @Override
    public boolean process(Transaction txn, PaymentDetails details) {
        System.out.println("[ICICI Bank] Outbound processing for transaction: " + txn.getTransactionId());
        return true;
    }
    @Override
    public String getBankName() { return "ICICI_BANK"; }
}

// Router Engine containing optimization or failure routing strategy rules
class RoutingEngine {
    private final List<BankAdapter> adapters = new ArrayList<>();

    public void registerAdapter(BankAdapter adapter) {
        adapters.add(adapter);
    }

    public BankAdapter selectBestRoute(PaymentInstrumentType instrumentType) {
        if (adapters.isEmpty()) {
            throw new IllegalStateException("No operational banking rails available.");
        }
        // Strategy/Rule: Route UPI transactions to HDFC, everything else to ICICI
        if (instrumentType == PaymentInstrumentType.UPI) {
            return adapters.get(0); // HDFC
        }
        return adapters.size() > 1 ? adapters.get(1) : adapters.get(0); // ICICI fallback
    }
}


// ================================================================================
// 4. CORE ORCHESTRATION SERVICE LAYER
// ================================================================================
class PaymentGatewayService {
    private final RoutingEngine routingEngine;

    public PaymentGatewayService(RoutingEngine routingEngine) {
        this.routingEngine = routingEngine;
    }

    public Transaction processPayment(PaymentRequest request) {
        // VERBAL NOTE TO INTERVIEWER: In production, distributed locking via Redis
        // on the Idempotency Key happens right here to isolate high-concurrency race conditions.

        // 1. Handle Idempotency / Double Spend Check
        if (StorageRegistry.transactionsByIdempotencyKey.containsKey(request.getIdempotencyKey())) {
            System.out.println("[Gateway] Idempotency Match Found! Returning existing record for Key: " + request.getIdempotencyKey());
            return StorageRegistry.transactionsByIdempotencyKey.get(request.getIdempotencyKey());
        }

        // 2. Validate Merchant Rules
        Merchant merchant = StorageRegistry.merchants.get(request.getMerchantId());
        if (merchant == null) {
            throw new IllegalArgumentException("Merchant validation failed. ID not found.");
        }
        if (!merchant.supports(request.getInstrumentType())) {
            throw new IllegalArgumentException("Payment instrument type disabled for this merchant client.");
        }

        // 3. Initialize Transaction Records
        Transaction transaction = new Transaction(request.getIdempotencyKey(), request.getMerchantId(), request.getAmount(), request.getInstrumentType());

        // Thread-safe map atomic registration prevents concurrent leakage during processing windows
        Transaction activeTransaction = StorageRegistry.transactionsByIdempotencyKey.putIfAbsent(request.getIdempotencyKey(), transaction);
        if (activeTransaction != null) {
            return activeTransaction; // Safely handle an exact millisecond tie breaker race condition
        }
        StorageRegistry.transactionsById.put(transaction.getTransactionId(), transaction);

        // 4. Select Bank Route and Forward Execution
        BankAdapter bankRoute = routingEngine.selectBestRoute(request.getInstrumentType());
        transaction.setGatewayBankName(bankRoute.getBankName());
        transaction.transitionStatus(PaymentStatus.PENDING_BANK_CALLBACK);

        boolean communicationSuccess = bankRoute.process(transaction, request.getDetails());
        if (!communicationSuccess) {
            transaction.transitionStatus(PaymentStatus.FAILED);
        }

        return transaction;
    }

    // Handles downstream async callbacks from external bank systems
    public void handleBankWebhook(String transactionId, boolean isSuccess) {
        Transaction txn = StorageRegistry.transactionsById.get(transactionId);
        if (txn == null) {
            System.out.println("[Webhook Error] Event received for unrecognized payment ID: " + transactionId);
            return;
        }

        PaymentStatus targetedStatus = isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        txn.transitionStatus(targetedStatus);
        System.out.println("[Webhook Processed] Transaction " + transactionId + " updated to status: " + txn.getStatus());

        // VERBAL NOTE TO INTERVIEWER: We would execute the Outbox pattern here to asynchronously
        // pass execution messages to the Merchant's registered endpoint callback systems.
    }

    public List<Transaction> getTransactionsForMerchant(String merchantId) {
        List<Transaction> merchantTxns = new ArrayList<>();
        for (Transaction t : StorageRegistry.transactionsById.values()) {
            if (t.getMerchantId().equals(merchantId)) {
                merchantTxns.add(t);
            }
        }
        return merchantTxns;
    }
}


// ================================================================================
// 5. DRIVER APPLICATION (PROVING MULTI-THREADED IDEMPOTENCY SAFETY)
// ================================================================================
public class PaymentGateway {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Bootstrapping Payment Gateway Engine ---");

        // Setup infrastructure registry configurations
        RoutingEngine routingEngine = new RoutingEngine();
        routingEngine.registerAdapter(new HdfcBankAdapter());
        routingEngine.registerAdapter(new IciciBankAdapter());

        PaymentGatewayService gatewayService = new PaymentGatewayService(routingEngine);

        // Onboard a dummy Merchant
        Merchant swiggy = new Merchant("MERCH_SWIGGY_01", "Swiggy Food Delivery");
        swiggy.enableInstrument(PaymentInstrumentType.UPI);
        StorageRegistry.merchants.put(swiggy.getMerchantId(), swiggy);

        // Construct identical duplicate requests to simulate user double-clicking on checkout page
        String duplicateIdempotencyKey = "REQ_ID_UNIQUE_9999";
        PaymentRequest clickOne = new PaymentRequest(duplicateIdempotencyKey, "MERCH_SWIGGY_01", 450.50, PaymentInstrumentType.UPI, new UpiDetails("user@oksbi"));
        PaymentRequest clickTwo = new PaymentRequest(duplicateIdempotencyKey, "MERCH_SWIGGY_01", 450.50, PaymentInstrumentType.UPI, new UpiDetails("user@oksbi"));

        System.out.println("\n--- Initiating Concurrent Double-Click Stress Test ---");
        ExecutorService concurrentExecutor = Executors.newFixedThreadPool(2);

        // Fire both operations in true parallel execution threads
        concurrentExecutor.submit(() -> {
            Transaction res = gatewayService.processPayment(clickOne);
            System.out.println("Thread 1 execution complete. Txn ID: " + res.getTransactionId() + " | Status: " + res.getStatus());
        });

        concurrentExecutor.submit(() -> {
            Transaction res = gatewayService.processPayment(clickTwo);
            System.out.println("Thread 2 execution complete. Txn ID: " + res.getTransactionId() + " | Status: " + res.getStatus());
        });

        concurrentExecutor.shutdown();
        concurrentExecutor.awaitTermination(3, TimeUnit.SECONDS);

        // Retrieve recorded states to perform assertions
        Transaction finalizedTxn = StorageRegistry.transactionsByIdempotencyKey.get(duplicateIdempotencyKey);

        System.out.println("\n--- Simulating Asynchronous Webhook Feedback Delivery from Bank ---");
        gatewayService.handleBankWebhook(finalizedTxn.getTransactionId(), true);

        System.out.println("\n--- Verification Assertions ---");
        System.out.println("Total unique transactions generated in storage: " + StorageRegistry.transactionsById.size());
        System.out.println("Final System Verified State: " + finalizedTxn.getStatus());
    }
}