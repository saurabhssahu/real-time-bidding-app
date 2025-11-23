package no.kobler.rtb.smoothing;

/**
 * Smoothing operations used by bidding flow.
 * Implementations may be in-memory or Redis-backed (future).
 */
public interface SmoothingService {

    /**
     * Try to reserve `amount` tokens for given campaignId.
     * Returns true if reservation succeeded (tokens deducted), false if not enough tokens.
     */
    boolean tryConsume(long campaignId, double amount);

    /**
     * Refund a previously reserved amount back to the campaign bucket.
     * Use when downstream persistence fails.
     */
    void refund(long campaignId, double amount);

    /**
     * For monitoring/debugging: get currently available tokens for campaign.
     */
    double availableTokens(long campaignId);
}
