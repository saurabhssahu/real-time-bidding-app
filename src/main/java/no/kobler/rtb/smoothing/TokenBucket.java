package no.kobler.rtb.smoothing;

import java.time.Instant;

/**
 * Simple thread-safe token bucket storing double tokens.
 * - capacity: maximum tokens bucket can hold
 * - refillRatePerSecond: tokens added per second
 * <p>
 * Methods are synchronized to keep operations atomic per bucket.
 */
public class TokenBucket {

    private final double capacity;
    private final double refillRatePerSecond;

    private double tokens;
    private long lastRefillEpochSeconds;

    public TokenBucket(double capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = capacity;
        this.lastRefillEpochSeconds = Instant.now().getEpochSecond();
    }

    private void refill() {
        long now = Instant.now().getEpochSecond();
        long elapsed = now - lastRefillEpochSeconds;
        if (elapsed <= 0) return;
        double refillAmount = elapsed * refillRatePerSecond;
        tokens = Math.min(capacity, tokens + refillAmount);
        lastRefillEpochSeconds = now;
    }

    /**
     * Try to consume `amount` tokens. Returns true if successful, false otherwise.
     */
    public synchronized boolean tryConsume(double amount) {
        refill();
        if (amount <= 0) return true; // nothing to consume
        if (tokens + 1e-9 >= amount) { // small epsilon for floating safety
            tokens -= amount;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Refund `amount` tokens back into the bucket (e.g., on failure after consumption).
     */
    public synchronized void refund(double amount) {
        if (amount <= 0) return;
        tokens = Math.min(capacity, tokens + amount);
    }

    /**
     * For debugging / metrics: current available tokens.
     */
    public synchronized double getAvailableTokens() {
        refill();
        return tokens;
    }
}

