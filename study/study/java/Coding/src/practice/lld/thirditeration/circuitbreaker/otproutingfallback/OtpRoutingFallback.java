package practice.lld.thirditeration.circuitbreaker.otproutingfallback;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone Java 17 OTP routing and fallback, without circuit breakers. */
public class OtpRoutingFallback {
    record OtpRequest(String requestId, String phone, String message) {}
    record OtpResult(String vendorId, String receiptId) {}
    enum FailureType { DEFINITIVE, UNKNOWN_OUTCOME }

    static class VendorException extends Exception {
        final FailureType type;
        VendorException(FailureType type, String message) {
            super(message); this.type = type;
        }
    }

    interface OtpVendor {
        String id();
        OtpResult send(OtpRequest request) throws VendorException;
    }

    // A simulated vendor. In real life each adapter maps its SDK errors to FailureType.
    static class MockVendor implements OtpVendor {
        private final String id;
        private final AtomicInteger failuresRemaining;
        private final FailureType failureType;
        MockVendor(String id, int failures, FailureType failureType) {
            this.id = id;
            this.failuresRemaining = new AtomicInteger(failures);
            this.failureType = failureType;
        }
        public String id() { return id; }
        public OtpResult send(OtpRequest request) throws VendorException {
            if (failuresRemaining.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
                throw new VendorException(failureType, id + " failed");
            System.out.println(id + " accepted OTP for " + request.phone());
            return new OtpResult(id, id + "-receipt-" + request.requestId());
        }
    }

    record VendorMetrics(double p95Millis, double successRate) {}
    interface VendorSelectionStrategy {
        List<OtpVendor> rank(List<OtpVendor> vendors, Map<String, VendorMetrics> metrics);
    }
    static class LowestLatencyStrategy implements VendorSelectionStrategy {
        public List<OtpVendor> rank(List<OtpVendor> vendors, Map<String, VendorMetrics> metrics) {
            return vendors.stream()
                    .sorted(Comparator.comparingDouble(v -> metrics.get(v.id()).p95Millis()))
                    .toList();
        }
    }

    static class OtpService {
        private final List<OtpVendor> vendors;
        private final Map<String, VendorMetrics> metrics;
        private final VendorSelectionStrategy strategy;
        OtpService(List<OtpVendor> vendors, Map<String, VendorMetrics> metrics,
                   VendorSelectionStrategy strategy) {
            this.vendors = List.copyOf(vendors);
            this.metrics = Map.copyOf(metrics);
            this.strategy = strategy;
            if (vendors.isEmpty()) throw new IllegalArgumentException("No vendors");
            for (OtpVendor vendor : vendors)
                if (!metrics.containsKey(vendor.id())) throw new IllegalArgumentException("Missing metrics");
        }
        OtpResult sendOtp(OtpRequest request) throws VendorException {
            VendorException last = null;
            for (OtpVendor vendor : strategy.rank(vendors, metrics)) {
                try { return vendor.send(request); }
                catch (VendorException ex) {
                    last = ex;
                    System.out.println(vendor.id() + ": " + ex.getMessage());
                    // Unknown outcome might mean the SMS was already accepted.
                    if (ex.type == FailureType.UNKNOWN_OUTCOME) throw ex;
                }
            }
            throw new VendorException(FailureType.DEFINITIVE,
                    "All vendors definitively failed; last=" + (last == null ? "none" : last.getMessage()));
        }
    }

    public static void main(String[] args) throws Exception {
        var vendors = List.<OtpVendor>of(
                new MockVendor("Gupshup", 1, FailureType.DEFINITIVE),
                new MockVendor("Twilio", 0, FailureType.DEFINITIVE),
                new MockVendor("MSG91", 0, FailureType.DEFINITIVE));
        var metrics = Map.of(
                "Gupshup", new VendorMetrics(100, .92),
                "Twilio", new VendorMetrics(150, .99),
                "MSG91", new VendorMetrics(200, .97));
        OtpService service = new OtpService(vendors, metrics, new LowestLatencyStrategy());
        System.out.println("Result: " + service.sendOtp(new OtpRequest("req-1", "+910000000000", "123456")));
        System.out.println("Result: " + service.sendOtp(new OtpRequest("req-2", "+910000000000", "654321")));
    }
}
