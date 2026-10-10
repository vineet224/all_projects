package practice.lld.thirditeration.ratelimiter.chatgptCAS;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

class RateLimitConfig {
    private final double capacity;
    private final double refillTokensPerNano;

    public RateLimitConfig(long maxTokens, long windowDurationSeconds) {
        this.capacity = (double) maxTokens;
        this.refillTokensPerNano = (double) maxTokens / (windowDurationSeconds * 1_000_000_000L);
    }

    public double getCapacity() { return capacity; }
    public double getRefillTokensPerNano() { return refillTokensPerNano; }
}

public class LockFreeTockenBucket {

    // 1. Immutable state snapshot: ensures tokens and timestamp update atomically
    private record State(double tokens, long lastRefillNanos) {}

    private final RateLimitConfig config;
    private final AtomicReference<State> stateRef;

    public LockFreeTockenBucket(RateLimitConfig config) {
        this.config = config;
        // Bucket starts completely full at creation time
        this.stateRef = new AtomicReference<>(new State(config.getCapacity(), System.nanoTime()));
    }

    public boolean tryConsume() {
        return tryConsume(1.0);
    }

    public boolean tryConsume(double tokensToConsume) {
        State current;
        State updated;

        // 2. Lock-free CAS retry loop
        do {
            current = stateRef.get();
            long now = System.nanoTime();

            // Calculate elapsed time (protect against non-monotonic clock issues)
            long elapsedNanos = Math.max(0L, now - current.lastRefillNanos());

            // Pure function: refill tokens up to capacity
            double refilledTokens = Math.min(
                    config.getCapacity(),
                    current.tokens() + (elapsedNanos * config.getRefillTokensPerNano())
            );

            // Rejection check: not enough tokens available
            if (refilledTokens < tokensToConsume) {
                // Optional optimization: refresh the timestamp & tokens even on rejection
                // if significant time elapsed, but simple rejection is standard.
                return false;
            }

            // Prepare new immutable state: deduct consumed tokens and advance timestamp
            updated = new State(refilledTokens - tokensToConsume, now);

            // 3. Atomic CAS: succeeds only if no other thread mutated stateRef in between
        } while (!stateRef.compareAndSet(current, updated));

        return true;
    }
}