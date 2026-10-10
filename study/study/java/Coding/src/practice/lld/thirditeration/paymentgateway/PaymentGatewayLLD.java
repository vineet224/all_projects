package practice.lld.thirditeration.paymentgateway;


import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class PaymentGatewayLLD {

    // ================= ENUMS =================

    enum PaymentMethod {
        UPI, CARD, NET_BANKING
    }

    enum PaymentStatus {
        CREATED,
        PROCESSING,
        PENDING,
        SUCCESS,
        FAILED
    }

    enum GatewayStatus {
        SUCCESS,
        FAILED,
        PENDING
    }

    // ================= REQUEST =================

    record PaymentRequest(
            String orderId,
            long amountInPaise,
            String currency,
            PaymentMethod method,
            String idempotencyKey
    ) {
        PaymentRequest {
            if (orderId == null || orderId.isBlank())
                throw new IllegalArgumentException("Invalid order");

            if (amountInPaise <= 0)
                throw new IllegalArgumentException("Invalid amount");

            if (currency == null || currency.isBlank())
                throw new IllegalArgumentException("Invalid currency");

            Objects.requireNonNull(method);

            if (idempotencyKey == null || idempotencyKey.isBlank())
                throw new IllegalArgumentException("Idempotency key required");
        }
    }

    // ================= GATEWAY RESULT =================

    record GatewayResult(
            GatewayStatus status,
            String gatewayReference
    ) {}

    // ================= PAYMENT ENTITY =================

    static class Payment {
        final String id;
        final PaymentRequest request;

        private PaymentStatus status = PaymentStatus.CREATED;
        private String gatewayReference;

        Payment(String id, PaymentRequest request) {
            this.id = id;
            this.request = request;
        }

        synchronized PaymentStatus getStatus() {
            return status;
        }

        synchronized String getGatewayReference() {
            return gatewayReference;
        }

        // Only one thread can claim an unprocessed payment.
        synchronized boolean claimProcessing() {
            if (status != PaymentStatus.CREATED)
                return false;

            status = PaymentStatus.PROCESSING;
            return true;
        }

        synchronized void applyResult(GatewayResult result) {
            if (status != PaymentStatus.PROCESSING)
                throw new IllegalStateException(
                        "Unexpected payment state: " + status
                );

            gatewayReference = result.gatewayReference();

            status = switch (result.status()) {
                case SUCCESS -> PaymentStatus.SUCCESS;
                case FAILED -> PaymentStatus.FAILED;
                case PENDING -> PaymentStatus.PENDING;
            };
        }

        synchronized void markPending() {
            if (status == PaymentStatus.PROCESSING)
                status = PaymentStatus.PENDING;
        }

        @Override
        public synchronized String toString() {
            return "Payment{id=" + id +
                    ", order=" + request.orderId() +
                    ", method=" + request.method() +
                    ", status=" + status +
                    ", gatewayRef=" + gatewayReference + "}";
        }
    }

    // ================= PROCESSOR STRATEGY =================

    interface PaymentProcessor {
        GatewayResult process(Payment payment);
    }

    static class UpiProcessor implements PaymentProcessor {
        @Override
        public GatewayResult process(Payment payment) {
            simulateDelay();

            System.out.println("Processing UPI: " + payment.id);

            return new GatewayResult(
                    GatewayStatus.SUCCESS,
                    "UPI-" + payment.id
            );
        }
    }

    static class CardProcessor implements PaymentProcessor {
        @Override
        public GatewayResult process(Payment payment) {
            simulateDelay();

            System.out.println("Processing Card: " + payment.id);

            return new GatewayResult(
                    GatewayStatus.SUCCESS,
                    "CARD-" + payment.id
            );
        }
    }

    static class NetBankingProcessor implements PaymentProcessor {
        @Override
        public GatewayResult process(Payment payment) {
            simulateDelay();

            System.out.println("Processing NetBanking: " + payment.id);

            return new GatewayResult(
                    GatewayStatus.SUCCESS,
                    "NB-" + payment.id
            );
        }
    }

    static void simulateDelay() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    // ================= REPOSITORY =================

    static class PaymentRepository {

        private final ConcurrentHashMap<String, Payment> byId =
                new ConcurrentHashMap<>();

        // Key -> Payment
        private final ConcurrentHashMap<String, Payment> byIdempotency =
                new ConcurrentHashMap<>();

        Payment findOrCreate(PaymentRequest request) {

            Payment candidate = new Payment(
                    UUID.randomUUID().toString(),
                    request
            );

            Payment existing = byIdempotency.putIfAbsent(
                    request.idempotencyKey(),
                    candidate
            );

            if (existing != null) {
                if (!existing.request.equals(request))
                    throw new IllegalArgumentException(
                            "Idempotency key reused with different request"
                    );

                return existing;
            }

            byId.put(candidate.id, candidate);
            return candidate;
        }

        Payment get(String paymentId) {
            Payment payment = byId.get(paymentId);

            if (payment == null)
                throw new IllegalArgumentException("Payment not found");

            return payment;
        }
    }

    // ================= PAYMENT SERVICE =================

    static class PaymentService {

        private final PaymentRepository repository;

        private final Map<PaymentMethod, PaymentProcessor> processors;

        PaymentService(
                PaymentRepository repository,
                Map<PaymentMethod, PaymentProcessor> processors
        ) {
            this.repository = repository;
            this.processors = Map.copyOf(processors);
        }

        public Payment pay(PaymentRequest request) {

            Payment payment = repository.findOrCreate(request);

            // Only the first caller can claim processing.
            if (!payment.claimProcessing())
                return payment;

            PaymentProcessor processor =
                    processors.get(request.method());

            if (processor == null) {
                payment.applyResult(
                        new GatewayResult(GatewayStatus.FAILED, null)
                );
                return payment;
            }

            try {
                GatewayResult result = processor.process(payment);
                payment.applyResult(result);

            } catch (Exception e) {
                // Unknown outcome: the gateway may have charged.
                payment.markPending();
            }

            return payment;
        }

        public Payment getPayment(String paymentId) {
            return repository.get(paymentId);
        }
    }

    // ================= MAIN / TESTS =================

    public static void main(String[] args) throws Exception {

        PaymentRepository repository = new PaymentRepository();

        Map<PaymentMethod, PaymentProcessor> processors =
                Map.of(
                        PaymentMethod.UPI, new UpiProcessor(),
                        PaymentMethod.CARD, new CardProcessor(),
                        PaymentMethod.NET_BANKING,
                        new NetBankingProcessor()
                );

        PaymentService service =
                new PaymentService(repository, processors);

        // TEST 1: Normal payment
        PaymentRequest request1 = new PaymentRequest(
                "ORDER-1",
                50000,
                "INR",
                PaymentMethod.UPI,
                "KEY-1"
        );

        Payment payment1 = service.pay(request1);
        System.out.println(payment1);

        // TEST 2: Same idempotency key
        Payment payment2 = service.pay(request1);

        System.out.println(
                "Same payment ID: " +
                        payment1.id.equals(payment2.id)
        );

        // TEST 3: Same key, different amount
        try {
            service.pay(new PaymentRequest(
                    "ORDER-1",
                    70000,
                    "INR",
                    PaymentMethod.UPI,
                    "KEY-1"
            ));
        } catch (IllegalArgumentException e) {
            System.out.println("Conflict rejected: " + e.getMessage());
        }

        // TEST 4: 10 concurrent requests with same key
        ExecutorService executor =
                Executors.newFixedThreadPool(10);

        PaymentRequest concurrentRequest = new PaymentRequest(
                "ORDER-2",
                100000,
                "INR",
                PaymentMethod.CARD,
                "KEY-CONCURRENT"
        );

        List<Future<Payment>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(
                    () -> service.pay(concurrentRequest)
            ));
        }

        Set<String> paymentIds = new HashSet<>();

        for (Future<Payment> future : futures) {
            paymentIds.add(future.get().id);
        }

        System.out.println(
                "Unique payment IDs: " + paymentIds.size()
        );

        executor.shutdown();
    }
}
