package no.kobler.rtb.smoothing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory smoothing service using TokenBucket per campaign.
 * <p>
 * Capacity and refill rate follow the requirement: max 10 NOK per 10 seconds.
 * - capacity = 10.0
 * - refill rate = 1.0 tokens per second (10 / 10)
 */
@Service
@ConditionalOnProperty(name = "smoothing.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySmoothingService implements SmoothingService {

    private static final Logger log = LoggerFactory.getLogger(InMemorySmoothingService.class);

    private final Map<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    private static final double CAPACITY = 10.0;
    private static final double REFILL_RATE_PER_SECOND = 1.0;

    /**
     * Return a TokenBucket instance for the given campaignId.
     * If the token bucket doesn't exist, create a new one with the given capacity and refill rate.
     * This method is thread-safe and will only create a single TokenBucket instance per campaignId.
     *
     * @param campaignId the campaignId to create or retrieve a TokenBucket for
     * @return a TokenBucket instance for the given campaignId
     */
    private TokenBucket bucketFor(long campaignId) {
        return buckets.computeIfAbsent(campaignId,
                id -> {
                    log.debug("Creating token bucket for campaignId={}", id);
                    return new TokenBucket(CAPACITY, REFILL_RATE_PER_SECOND);
                });
    }

    /**
     * Try to consume `amount` tokens from the token bucket for the given campaignId.
     * Returns true if successful, false if not enough tokens available.
     * <p>
     * The method is thread-safe and will only create a single TokenBucket instance per campaignId.
     *
     * @param campaignId the campaignId to try consuming tokens from
     * @param amount     the amount of tokens to try consuming
     * @return true if successful, false if not enough tokens available
     */
    @Override
    public boolean tryConsume(long campaignId, double amount) {
        TokenBucket bucket = bucketFor(campaignId);
        boolean ok = bucket.tryConsume(amount);
        log.debug("tryConsume campaignId={} amount={} -> {}", campaignId, amount, ok);
        log.debug("Available tokens: {} for campaignId={}", bucket.getAvailableTokens(), campaignId);
        return ok;
    }

    /**
     * Refund a previously reserved amount back to the campaign bucket.
     * Use when downstream persistence fails.
     * <p>
     * The method is thread-safe and will only create a single TokenBucket instance per campaignId.
     *
     * @param campaignId the campaignId to refund tokens for
     * @param amount     the amount of tokens to refund
     */
    @Override
    public void refund(long campaignId, double amount) {
        TokenBucket bucket = bucketFor(campaignId);
        bucket.refund(amount);
        log.debug("refund campaignId={} amount={}", campaignId, amount);
    }

    /**
     * For monitoring/debugging: get currently available tokens for campaign.
     * <p>
     * The method is thread-safe and will only create a single TokenBucket instance per campaignId.
     *
     * @param campaignId the campaignId to get available tokens for
     * @return the currently available tokens for the campaign
     */
    @Override
    public double availableTokens(long campaignId) {
        TokenBucket bucket = bucketFor(campaignId);
        return bucket.getAvailableTokens();
    }
}
