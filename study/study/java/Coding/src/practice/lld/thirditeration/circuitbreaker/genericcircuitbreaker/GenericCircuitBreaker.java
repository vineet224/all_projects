package practice.lld.thirditeration.circuitbreaker.genericcircuitbreaker;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Standalone Java 17 circuit breaker. Compile: javac GenericCircuitBreaker.java */
public class GenericCircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }

    static final class CircuitOpenException extends RuntimeException {
        CircuitOpenException() { super("Circuit is OPEN or probe capacity exhausted"); }
    }

    static final class Breaker {
        private final ReentrantLock lock = new ReentrantLock();
        private final int threshold, maxProbes;
        private final long cooldownNanos;
        private State state = State.CLOSED;
        private int consecutiveFailures, activeProbes, successfulProbes;
        private long openedAt, generation;

        Breaker(int threshold, Duration cooldown, int maxProbes) {
            if (threshold < 1 || maxProbes < 1 || cooldown.isNegative())
                throw new IllegalArgumentException("Invalid breaker configuration");
            this.threshold = threshold;
            this.cooldownNanos = cooldown.toNanos();
            this.maxProbes = maxProbes;
        }

        // A permit remembers which breaker generation admitted this call.
        private record Permit(long generation, boolean probe) {}

        private Permit acquire() {
            lock.lock();
            try {
                if (state == State.OPEN && System.nanoTime() - openedAt >= cooldownNanos) {
                    state = State.HALF_OPEN;
                    generation++;
                    activeProbes = 0;
                    successfulProbes = 0;
                }
                if (state == State.OPEN) throw new CircuitOpenException();
                if (state == State.HALF_OPEN) {
                    if (activeProbes >= maxProbes) throw new CircuitOpenException();
                    activeProbes++;
                    return new Permit(generation, true);
                }
                return new Permit(generation, false);
            } finally { lock.unlock(); }
        }

        private void finish(Permit permit, boolean success) {
            lock.lock();
            try {
                // Ignore old in-flight results after state generation changes.
                if (permit.generation != generation) return;
                if (permit.probe) {
                    if (state != State.HALF_OPEN) return;
                    activeProbes--;
                    if (!success) { open(); return; }
                    successfulProbes++;
                    if (successfulProbes >= maxProbes && activeProbes == 0) {
                        state = State.CLOSED;
                        consecutiveFailures = 0;
                        generation++;
                    }
                } else if (state == State.CLOSED) {
                    if (success) consecutiveFailures = 0;
                    else if (++consecutiveFailures >= threshold) open();
                }
            } finally { lock.unlock(); }
        }

        private void open() {
            state = State.OPEN;
            openedAt = System.nanoTime();
            generation++;
            activeProbes = successfulProbes = 0;
        }

        <T> T execute(Callable<T> operation) throws Exception {
            Permit permit = acquire();
            boolean success = false;
            try {
                T result = operation.call();
                success = true;
                return result;
            } finally {
                finish(permit, success);
            }
        }

        State state() {
            lock.lock();
            try { return state; } finally { lock.unlock(); }
        }
    }

    public static void main(String[] args) throws Exception {
        Breaker breaker = new Breaker(2, Duration.ofMillis(300), 1);
        for (int i = 0; i < 2; i++) {
            try { breaker.execute(() -> { throw new IllegalStateException("vendor down"); }); }
            catch (IllegalStateException ignored) { }
        }
        System.out.println("After two failures: " + breaker.state());
        try { breaker.execute(() -> "should not run"); }
        catch (CircuitOpenException e) { System.out.println("Rejected immediately"); }
        Thread.sleep(350);
        System.out.println("Recovery probe: " + breaker.execute(() -> "OK"));
        System.out.println("After probe: " + breaker.state());

        // Multiple concurrent requests can safely share the same breaker.
        AtomicInteger calls = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++)
                futures.add(workers.submit(() -> {
                    try { breaker.execute(() -> calls.incrementAndGet()); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }));
            for (Future<?> future : futures) future.get();
        } finally { workers.shutdownNow(); }
        System.out.println("Successful concurrent calls: " + calls.get());
    }
}
