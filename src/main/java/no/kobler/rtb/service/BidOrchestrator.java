package no.kobler.rtb.service;

import no.kobler.rtb.service.bids.BidDecision;
import no.kobler.rtb.service.bids.BiddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class BidOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BidOrchestrator.class);

    private final ExecutorService executor;
    private final BiddingService biddingService;

    @Value("${smoothing.bid-timeout-ms:500}")
    private long defaultTimeoutMs;

    public BidOrchestrator(@Qualifier("bidExecutorService") ExecutorService executor,
                           BiddingService biddingService) {
        this.executor = executor;
        this.biddingService = biddingService;
    }

    /**
     * Evaluate bid with a timeout. Returns Optional.empty() on timeout or failure.
     * <p>
     * Evaluates a bid by submitting a task to the executor and waiting for the result.
     * If the timeout is reached, the task is cancelled and an empty Optional is returned.
     * If the task is interrupted, the task is cancelled, the current thread is interrupted, and an empty Optional is returned.
     * If the task throws an exception, the task is cancelled, the exception is logged, and an empty Optional is returned.
     * </p>
     *
     * @param bidId     the id of the bid to evaluate
     * @param keywords  the set of keywords to evaluate
     * @param timeoutMs the timeout in milliseconds
     * @return an Optional containing the result of the evaluation, or an empty Optional if the evaluation timed out, was interrupted, or threw an exception
     */
    public Optional<BidDecision> evaluateWithTimeout(long bidId, Set<String> keywords, long timeoutMs) {
        Future<BidDecision> submitted = executor.submit(() -> biddingService.evaluateBid(bidId, keywords));
        try {
            BidDecision result = submitted.get(timeoutMs, TimeUnit.MILLISECONDS);
            return Optional.ofNullable(result);
        } catch (TimeoutException timeoutException) {
            submitted.cancel(true);
            log.debug("Bid {} timed out after {}ms", bidId, timeoutMs);
            return Optional.empty();
        } catch (InterruptedException interruptedException) {
            submitted.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("Bid {} interrupted", bidId);
            return Optional.empty();
        } catch (ExecutionException executionException) {
            log.error("Bid {} evaluation failed: {}", bidId, executionException.getMessage());
            return Optional.empty();
        }
    }

    public Optional<BidDecision> evaluateWithDefaultTimeout(long bidId, Set<String> keywords) {
        return evaluateWithTimeout(bidId, keywords, defaultTimeoutMs);
    }
}
