package practice.lld.thirditeration.circuitbreaker.fullthirdpartyresilience;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Generic, standalone Java 17 resilience layer. No third-party libraries. */
public class ThirdPartyResilienceLayer {
    enum FailureKind { DEFINITIVE_TRANSIENT, PERMANENT, UNKNOWN_OUTCOME }
    enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    static class IntegrationException extends Exception {
        final FailureKind kind;
        IntegrationException(FailureKind kind, String message) { super(message); this.kind = kind; }
        IntegrationException(FailureKind kind, String message, Throwable cause) {
            super(message, cause); this.kind = kind;
        }
    }
    static class CircuitRejected extends Exception { CircuitRejected(String vendor) { super(vendor + " circuit rejected"); } }

    interface Vendor<Req, Res> {
        String id();
        Res invoke(Req request) throws IntegrationException;
    }
    interface RoutingStrategy<Req, Res> {
        List<Vendor<Req, Res>> rank(List<Vendor<Req, Res>> vendors, Req request);
    }
    static class PriorityRouting<Req, Res> implements RoutingStrategy<Req, Res> {
        public List<Vendor<Req, Res>> rank(List<Vendor<Req, Res>> vendors, Req request) {
            return vendors; // configured order
        }
    }
    record RetryPolicy(int maxAttemptsPerVendor, Duration delay) {
        RetryPolicy {
            if (maxAttemptsPerVendor < 1 || delay.isNegative()) throw new IllegalArgumentException();
        }
    }
    record Config(int failureThreshold, Duration cooldown, Duration perAttemptTimeout,
                  Duration totalTimeout, RetryPolicy retry) {
        Config {
            if (failureThreshold < 1 || cooldown.isNegative() || perAttemptTimeout.isZero()
                    || perAttemptTimeout.isNegative() || totalTimeout.isZero() || totalTimeout.isNegative())
                throw new IllegalArgumentException("Invalid configuration");
        }
    }

    // Single HALF_OPEN probe; epoch avoids stale completions reopening/closing a newer state.
    static class Breaker {
        private final ReentrantLock lock = new ReentrantLock();
        private final int threshold;
        private final long cooldownNanos;
        private CircuitState state = CircuitState.CLOSED;
        private int failures;
        private long openedAt, epoch;
        private boolean probeActive;
        record Permit(long epoch, boolean probe) {}
        Breaker(int threshold, Duration cooldown) {
            this.threshold = threshold; this.cooldownNanos = cooldown.toNanos();
        }
        Permit acquire(String vendorId) throws CircuitRejected {
            lock.lock();
            try {
                if (state == CircuitState.OPEN && System.nanoTime() - openedAt >= cooldownNanos) {
                    state = CircuitState.HALF_OPEN; epoch++; probeActive = false;
                }
                if (state == CircuitState.OPEN || (state == CircuitState.HALF_OPEN && probeActive))
                    throw new CircuitRejected(vendorId);
                if (state == CircuitState.HALF_OPEN) probeActive = true;
                return new Permit(epoch, state == CircuitState.HALF_OPEN);
            } finally { lock.unlock(); }
        }
        void complete(Permit permit, boolean success, boolean countAsFailure) {
            lock.lock();
            try {
                if (permit.epoch != epoch) return;
                if (permit.probe && state == CircuitState.HALF_OPEN) {
                    probeActive = false;
                    if (success || !countAsFailure) {
                        state = CircuitState.CLOSED; failures = 0; epoch++;
                    } else open();
                } else if (state == CircuitState.CLOSED) {
                    if (success) failures = 0;
                    else if (countAsFailure && ++failures >= threshold) open();
                }
            } finally { lock.unlock(); }
        }
        private void open() { state = CircuitState.OPEN; openedAt = System.nanoTime(); epoch++; }
        CircuitState state() { lock.lock(); try { return state; } finally { lock.unlock(); } }
    }

    static class Engine<Req, Res> implements AutoCloseable {
        private final List<Vendor<Req, Res>> vendors;
        private final RoutingStrategy<Req, Res> routing;
        private final Config config;
        private final Map<String, Breaker> breakers = new HashMap<>();
        // Workers must be sized/bounded for expected load; see production caveats.
        private final ExecutorService ioWorkers;
        private final boolean ownsWorkers;

        Engine(List<Vendor<Req, Res>> vendors, RoutingStrategy<Req, Res> routing,
               Config config, ExecutorService ioWorkers) {
            if (vendors.isEmpty()) throw new IllegalArgumentException("No vendors");
            this.vendors = List.copyOf(vendors);
            this.routing = routing;
            this.config = config;
            this.ioWorkers = ioWorkers;
            this.ownsWorkers = false;
            for (Vendor<Req, Res> vendor : vendors)
                if (breakers.putIfAbsent(vendor.id(),
                        new Breaker(config.failureThreshold(), config.cooldown())) != null)
                    throw new IllegalArgumentException("Duplicate vendor id: " + vendor.id());
        }

        Res execute(Req request) throws IntegrationException, InterruptedException {
            long deadline = System.nanoTime() + config.totalTimeout().toNanos();
            IntegrationException last = null;
            for (Vendor<Req, Res> vendor : routing.rank(vendors, request)) {
                Breaker breaker = breakers.get(vendor.id());
                if (breaker == null) throw new IllegalArgumentException("Unknown routed vendor");
                for (int attempt = 1; attempt <= config.retry().maxAttemptsPerVendor(); attempt++) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0)
                        throw new IntegrationException(FailureKind.UNKNOWN_OUTCOME, "Total deadline exceeded", last);
                    Breaker.Permit permit;
                    try { permit = breaker.acquire(vendor.id()); }
                    catch (CircuitRejected ex) { break; } // try next vendor

                    boolean success = false;
                    boolean countFailure = true;
                    try {
                        long timeout = Math.min(remaining, config.perAttemptTimeout().toNanos());
                        Res result = callWithTimeout(vendor, request, timeout);
                        success = true;
                        return result;
                    } catch (IntegrationException ex) {
                        last = ex;
                        countFailure = ex.kind != FailureKind.PERMANENT;
                        if (ex.kind == FailureKind.UNKNOWN_OUTCOME) throw ex;
                        if (ex.kind == FailureKind.PERMANENT) throw ex;
                        System.out.println(vendor.id() + " transient failure on attempt " + attempt);
                    } finally {
                        breaker.complete(permit, success, countFailure);
                    }
                    if (attempt < config.retry().maxAttemptsPerVendor()) {
                        long sleepNanos = Math.min(config.retry().delay().toNanos(),
                                Math.max(0, deadline - System.nanoTime()));
                        if (sleepNanos > 0) TimeUnit.NANOSECONDS.sleep(sleepNanos);
                    }
                }
            }
            throw new IntegrationException(FailureKind.DEFINITIVE_TRANSIENT,
                    "All eligible vendors failed or circuits rejected", last);
        }

        private Res callWithTimeout(Vendor<Req, Res> vendor, Req request, long nanos)
                throws IntegrationException, InterruptedException {
            Future<Res> future;
            try { future = ioWorkers.submit(() -> vendor.invoke(request)); }
            catch (RejectedExecutionException ex) {
                throw new IntegrationException(FailureKind.DEFINITIVE_TRANSIENT,
                        "Local worker capacity exhausted", ex);
            }
            try { return future.get(nanos, TimeUnit.NANOSECONDS); }
            catch (TimeoutException ex) {
                future.cancel(true); // best effort; cannot undo external side effects
                throw new IntegrationException(FailureKind.UNKNOWN_OUTCOME, "Vendor timeout", ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IntegrationException ie) throw ie;
                throw new IntegrationException(FailureKind.UNKNOWN_OUTCOME, "Unexpected vendor error", cause);
            } catch (InterruptedException ex) {
                future.cancel(true);
                throw ex;
            }
        }
        CircuitState state(String id) { return breakers.get(id).state(); }
        public void close() { if (ownsWorkers) ioWorkers.shutdownNow(); }
    }

    record OtpRequest(String requestId, String phone, String otp) {}
    record OtpReceipt(String vendorId, String receipt) {}
    static class DemoOtpVendor implements Vendor<OtpRequest, OtpReceipt> {
        private final String id;
        private final AtomicInteger transientFailures;
        DemoOtpVendor(String id, int failures) {
            this.id = id; this.transientFailures = new AtomicInteger(failures);
        }
        public String id() { return id; }
        public OtpReceipt invoke(OtpRequest request) throws IntegrationException {
            if (transientFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0)
                throw new IntegrationException(FailureKind.DEFINITIVE_TRANSIENT, "HTTP 503 before acceptance");
            return new OtpReceipt(id, id + "-" + request.requestId());
        }
    }

    public static void main(String[] args) throws Exception {
        // Caller owns the worker pool and shuts it down after all integrations finish.
        ExecutorService io = Executors.newFixedThreadPool(8);
        try {
            Config config = new Config(2, Duration.ofMillis(300), Duration.ofMillis(500),
                    Duration.ofSeconds(3), new RetryPolicy(2, Duration.ofMillis(25)));
            Engine<OtpRequest, OtpReceipt> engine = new Engine<>(
                    List.of(new DemoOtpVendor("Gupshup", 3), new DemoOtpVendor("Twilio", 0)),
                    new PriorityRouting<>(), config, io);
            System.out.println("Result: " + engine.execute(new OtpRequest("otp-1", "+910000000000", "123456")));
            System.out.println("Gupshup circuit: " + engine.state("Gupshup"));
            System.out.println("Result: " + engine.execute(new OtpRequest("otp-2", "+910000000000", "654321")));
        } finally { io.shutdownNow(); }
    }
}
