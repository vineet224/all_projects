package practice.lld.thirditeration.circuitbreaker.otpCircuitFallback;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Standalone Java 17 OTP vendor fallback with one breaker per vendor. */
public class OtpCircuitBreakerFallback {
    enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    enum FailureType { DEFINITIVE, UNKNOWN_OUTCOME }
    record Request(String id, String phone, String otp) {}
    record Receipt(String vendor, String receiptId) {}

    static class VendorFailure extends Exception {
        final FailureType type;
        VendorFailure(FailureType type, String message) { super(message); this.type = type; }
    }
    static class CircuitUnavailable extends Exception { CircuitUnavailable(String id) { super(id + " circuit unavailable"); } }

    interface Vendor {
        String id();
        Receipt send(Request request) throws VendorFailure;
    }
    static class DemoVendor implements Vendor {
        private final String id;
        private int failuresRemaining;
        DemoVendor(String id, int failuresRemaining) {
            this.id = id; this.failuresRemaining = failuresRemaining;
        }
        public String id() { return id; }
        public synchronized Receipt send(Request request) throws VendorFailure {
            if (failuresRemaining-- > 0)
                throw new VendorFailure(FailureType.DEFINITIVE, id + " returned 503");
            return new Receipt(id, id + "-" + request.id());
        }
    }

    static class Breaker {
        private final ReentrantLock lock = new ReentrantLock();
        private final int threshold;
        private final long cooldownNanos;
        private CircuitState state = CircuitState.CLOSED;
        private int failures;
        private long openedAt, generation;
        private boolean probeInFlight;
        private record Permit(long generation, boolean probe) {}
        Breaker(int threshold, Duration cooldown) {
            if (threshold < 1 || cooldown.isNegative()) throw new IllegalArgumentException();
            this.threshold = threshold; this.cooldownNanos = cooldown.toNanos();
        }
        private Permit acquire(String vendorId) throws CircuitUnavailable {
            lock.lock();
            try {
                if (state == CircuitState.OPEN && System.nanoTime() - openedAt >= cooldownNanos) {
                    state = CircuitState.HALF_OPEN; generation++; probeInFlight = false;
                }
                if (state == CircuitState.OPEN || (state == CircuitState.HALF_OPEN && probeInFlight))
                    throw new CircuitUnavailable(vendorId);
                if (state == CircuitState.HALF_OPEN) probeInFlight = true;
                return new Permit(generation, state == CircuitState.HALF_OPEN);
            } finally { lock.unlock(); }
        }
        private void complete(Permit permit, boolean success) {
            lock.lock();
            try {
                if (permit.generation != generation) return;
                if (permit.probe && state == CircuitState.HALF_OPEN) {
                    if (success) { state = CircuitState.CLOSED; failures = 0; generation++; }
                    else open();
                    probeInFlight = false;
                } else if (state == CircuitState.CLOSED) {
                    if (success) failures = 0;
                    else if (++failures >= threshold) open();
                }
            } finally { lock.unlock(); }
        }
        private void open() {
            state = CircuitState.OPEN; openedAt = System.nanoTime(); generation++;
        }
        Receipt execute(Vendor vendor, Request request) throws VendorFailure, CircuitUnavailable {
            Permit permit = acquire(vendor.id());
            boolean success = false;
            try {
                Receipt result = vendor.send(request);
                success = true;
                return result;
            } finally { complete(permit, success); }
        }
        CircuitState state() {
            lock.lock(); try { return state; } finally { lock.unlock(); }
        }
    }

    static class OtpService {
        private final List<Vendor> priorityOrder;
        private final Map<String, Breaker> breakers = new HashMap<>();
        OtpService(List<Vendor> priorityOrder, int threshold, Duration cooldown) {
            this.priorityOrder = List.copyOf(priorityOrder);
            if (priorityOrder.isEmpty()) throw new IllegalArgumentException("No vendors");
            for (Vendor vendor : priorityOrder)
                if (breakers.putIfAbsent(vendor.id(), new Breaker(threshold, cooldown)) != null)
                    throw new IllegalArgumentException("Duplicate vendor id");
        }
        Receipt send(Request request) throws VendorFailure {
            for (Vendor vendor : priorityOrder) {
                try { return breakers.get(vendor.id()).execute(vendor, request); }
                catch (CircuitUnavailable ex) { System.out.println("Skip: " + ex.getMessage()); }
                catch (VendorFailure ex) {
                    System.out.println("Failure: " + ex.getMessage());
                    // Do not resend if delivery acceptance is uncertain.
                    if (ex.type == FailureType.UNKNOWN_OUTCOME) throw ex;
                }
            }
            throw new VendorFailure(FailureType.DEFINITIVE, "No healthy vendor succeeded");
        }
        CircuitState state(String vendorId) { return breakers.get(vendorId).state(); }
    }

    public static void main(String[] args) throws Exception {
        OtpService service = new OtpService(
                List.of(new DemoVendor("Gupshup", 2), new DemoVendor("Twilio", 0)),
                2, Duration.ofMillis(250));
        System.out.println(service.send(new Request("r1", "+910000000000", "123456")));
        System.out.println(service.send(new Request("r2", "+910000000000", "123456")));
        System.out.println("Gupshup circuit: " + service.state("Gupshup"));
        System.out.println(service.send(new Request("r3", "+910000000000", "123456")));
        Thread.sleep(300);
        System.out.println("Recovery: " + service.send(new Request("r4", "+910000000000", "123456")));
        System.out.println("Gupshup circuit: " + service.state("Gupshup"));
    }
}
