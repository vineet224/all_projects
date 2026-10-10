package practice.lld.thirditeration.ratelimiter.chatgptsynchronized;

import java.util.concurrent.ConcurrentHashMap;

class RateLimitConfig {
    private final int capacity;
    private final double refillTokensPerNano;

    public RateLimitConfig(int capacity, int windowInSeconds) {
        this.capacity = capacity;
        this.refillTokensPerNano = (double) capacity / (windowInSeconds * 1_000_000_000L);
    }

    public int getCapacity() { return capacity; }
    public double getRefillTokensPerNano() { return refillTokensPerNano; }
}

class TokenBucket {
    private final RateLimitConfig config;
    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(RateLimitConfig config) {
        this.config = config;
        this.tokens = config.getCapacity(); // Start full
        this.lastRefillNanos = System.nanoTime();
    }

    // Locks ONLY requests belonging to this specific bucket/IP
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos > 0) {
            double tokensToAdd = elapsedNanos * config.getRefillTokensPerNano();
            tokens = Math.min(config.getCapacity(), tokens + tokensToAdd);
            lastRefillNanos = now;
        }
    }
}

public class RateLimiter {
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitConfig config;

    public RateLimiter(RateLimitConfig config) {
        this.config = config;
    }

    public boolean allowRequest(String clientIp) {
        // computeIfAbsent is atomic: creates one bucket per unique IP
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(config));
        return bucket.tryConsume();
    }
}