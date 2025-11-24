package no.kobler.rtb.service;

import no.kobler.rtb.service.bids.BidDecision;
import no.kobler.rtb.service.bids.BiddingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidOrchestratorTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("fast evaluation returns decision")
    void fastEvaluationReturnsDecision() {
        var biddingService = mock(BiddingService.class);
        when(biddingService.evaluateBid(1L, Set.of("kobler"))).thenReturn(new BidDecision(true, 3.2));

        var orchestrator = new BidOrchestrator(executor, biddingService);

        Optional<BidDecision> optionalBidDecision = orchestrator.evaluateWithTimeout(1L, Set.of("kobler"), 500);

        assertThat(optionalBidDecision).isPresent();
        assertThat(optionalBidDecision.get().bid()).isTrue();
        assertThat(optionalBidDecision.get().bidAmount()).isEqualTo(3.2);
        verify(biddingService, times(1)).evaluateBid(1L, Set.of("kobler"));
    }

    @Test
    @DisplayName("slow evaluation times out and returns empty")
    void slowEvaluationTimesOut() {
        var biddingService = mock(BiddingService.class);
        // Simulate slow evaluation: sleep inside mock
        when(biddingService.evaluateBid(2L, Set.of("x"))).thenAnswer(invocation -> {
            Thread.sleep(600); // longer than orchestrator timeout
            return new BidDecision(true, 1.0);
        });

        var orchestrator = new BidOrchestrator(executor, biddingService);

        Optional<BidDecision> optionalBidDecision = orchestrator.evaluateWithTimeout(2L, Set.of("x"), 250);

        assertThat(optionalBidDecision).isEmpty();
        verify(biddingService, times(1)).evaluateBid(2L, Set.of("x")); // executed but timed out
    }

    @Test
    @DisplayName("interrupted evaluation returns empty")
    void interruptedEvaluationReturnsEmpty() throws Exception {
        // mock bidding service that sleeps (simulates long running work)
        var biddingService = mock(BiddingService.class);
        when(biddingService.evaluateBid(1L, Set.of("a"))).thenAnswer(invocation -> {
            Thread.sleep(1000); // long-running task
            return new BidDecision(true, 1.0);
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Create orchestrator with correct constructor order (BiddingService, ExecutorService)
            var orchestrator = new BidOrchestrator(executor, biddingService);

            // We'll run evaluateWithTimeout on a separate thread so we can interrupt that thread
            AtomicReference<Optional<BidDecision>> resultRef = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                // call with a generous timeout so we don't hit the TimeoutException branch
                Optional<BidDecision> res = orchestrator.evaluateWithTimeout(1L, Set.of("a"), 2000L);
                resultRef.set(res);
            });

            caller.start();

            // Give the caller a moment to submit the task and block on future.get()
            Thread.sleep(100);

            // Now interrupt the calling thread to trigger InterruptedException inside evaluateWithTimeout
            caller.interrupt();

            // Wait for caller to finish
            caller.join(2000);

            // Assert the orchestrator returned empty due to interruption
            assertThat(resultRef.get()).isEmpty();

            // verify the bidding service was invoked once (task was executed)
            verify(biddingService, times(1)).evaluateBid(1L, Set.of("a"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("failed evaluation returns empty")
    void failedEvaluationReturnsEmpty() {
        var biddingService = mock(BiddingService.class);
        // Simulate failed evaluation: throw exception inside mock
        when(biddingService.evaluateBid(2L, Set.of("b"))).thenThrow(new RuntimeException("failed"));

        var orchestrator = new BidOrchestrator(executor, biddingService);

        Optional<BidDecision> optionalBidDecision = orchestrator.evaluateWithTimeout(2L, Set.of("b"), 250);

        assertThat(optionalBidDecision).isEmpty();
        verify(biddingService, times(1)).evaluateBid(2L, Set.of("b")); // executed but failed
    }
}
