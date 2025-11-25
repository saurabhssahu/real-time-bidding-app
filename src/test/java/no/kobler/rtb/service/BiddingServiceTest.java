package no.kobler.rtb.service;

import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import no.kobler.rtb.service.bids.BidDecision;
import no.kobler.rtb.service.bids.BiddingService;
import no.kobler.rtb.smoothing.SmoothingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BiddingServiceTest {

    private CampaignRepository campaignRepository;
    private BiddingService biddingService;
    private SmoothingService smoothingService; // mock

    @BeforeEach
    void setup() {
        campaignRepository = mock(CampaignRepository.class);
        smoothingService = mock(SmoothingService.class);
        // deterministic random to control prices: will generate predictable doubles
        Random deterministicRandom = new Random(123L);
        // BiddingService constructor: (CampaignRepository repo, Random random, SmoothingService service)
        biddingService = new BiddingService(campaignRepository, deterministicRandom, smoothingService);
    }

    @Test
    @DisplayName("when no campaigns match bid keywords -> no bid (empty)")
    void evaluateBid_noMatchingCampaigns_returnsNoBid() {
        // repository returns two campaigns whose keywords do not match
        Campaign campaign1 = new Campaign("A", Set.of("alpha"), new BigDecimal("100.0"));
        campaign1.setId(1L);
        Campaign campaign2 = new Campaign("B", Set.of("beta"), new BigDecimal("100.0"));
        campaign2.setId(2L);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign1, campaign2));

        var decision = biddingService.evaluateBid(1L, Set.of("Kobler"));

        assertThat(decision.bid()).isFalse();
        verify(campaignRepository, never()).incrementSpendingIfNotExceed(anyLong(), any(BigDecimal.class));
        verifyNoInteractions(smoothingService);
    }

    @Test
    @DisplayName("when a single campaign matches and has budget -> return bid and update spending")
    void evaluateBid_singleMatchingCampaignWithBudget_returnsBid() {
        Campaign campaign = new Campaign("Camp", Set.of(" Kobler "), new BigDecimal("100.0"));
        campaign.setId(10L);
        campaign.setSpending(BigDecimal.ZERO);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign));
        // allow smoothing reservation
        when(smoothingService.tryConsume(anyLong(), anyDouble())).thenReturn(true);
        // simulate successful atomic DB update
        when(campaignRepository.incrementSpendingIfNotExceed(eq(10L), any(BigDecimal.class))).thenReturn(1);

        var decision = biddingService.evaluateBid(42L, Set.of("kobler"));

        // We expect a bid
        assertThat(decision.bid()).isTrue();
        double amount = Math.round(decision.bidAmount() * 100) / 100.0;
        assertThat(amount).isBetween(0.0, 10.0);

        // verify DB atomic increment was attempted with the correct id and amount
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(campaignRepository, times(1))
                .incrementSpendingIfNotExceed(eq(10L), amountCaptor.capture());
        BigDecimal passedAmount = amountCaptor.getValue();
        assertThat(passedAmount).isEqualByComparingTo(BigDecimal.valueOf(amount));

        // verify smoothing reservation attempted
        verify(smoothingService, times(1)).tryConsume(eq(10L), eq(amount));
    }

    @Test
    @DisplayName("when matching campaign exists but no budget-> no bid and no update")
    void evaluateBid_singleMatchingCampaignNoBudget_returnsNoBid() {
        Campaign poor = new Campaign("Poor", Set.of("Kobler"), new BigDecimal("1.0"));
        poor.setId(5L);
        poor.setSpending(new BigDecimal("1.0")); // already spent full budget

        when(campaignRepository.findAll()).thenReturn(List.of(poor));

        var decision = biddingService.evaluateBid(9L, Set.of("KOBLER"));

        assertThat(decision.bid()).isFalse();
        verify(campaignRepository, never()).incrementSpendingIfNotExceed(anyLong(), any(BigDecimal.class));
        // smoothing should not be called because budget check fails before smoothing
        verifyNoInteractions(smoothingService);
    }

    @Test
    @DisplayName("Single matching campaign with insufficient budget should return no-bid")
    void evaluateBid_singleMatchingCampaignLessBudget_returnsNoBid() {
        // Arrange
        var random = mock(Random.class);

        Campaign campaign = new Campaign("C1", Set.of("sports"), new BigDecimal("5.0"));
        campaign.setId(1L);
        campaign.setSpending(new BigDecimal("4.99")); // Almost spent
        when(campaignRepository.findAll()).thenReturn(List.of(campaign));
        when(random.nextDouble()).thenReturn(0.5); // Would be 5.0, but budget is 5.0 - 4.99 = 0.01

        // Act
        var decision = biddingService.evaluateBid(1L, Set.of("sports"));

        // Assert
        assertThat(decision.bid()).isFalse();
        verify(campaignRepository, never()).incrementSpendingIfNotExceed(anyLong(), any(BigDecimal.class));
        verifyNoInteractions(smoothingService);
    }

    @Test
    @DisplayName("smoothing denies reservation -> no bid and no update")
    void evaluateBid_smoothingDeniesReservation_noBid() {
        Campaign campaign = new Campaign("Camp", Set.of("Kobler"), new BigDecimal("100.0"));
        campaign.setId(20L);
        campaign.setSpending(BigDecimal.ZERO);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign));
        // smoothing denies token reservation
        when(smoothingService.tryConsume(eq(20L), anyDouble())).thenReturn(false);

        BidDecision decision = biddingService.evaluateBid(101L, Set.of("kObLeR"));

        assertThat(decision.bid()).isFalse();
        verify(campaignRepository, never()).incrementSpendingIfNotExceed(anyLong(), any(BigDecimal.class));
        verify(smoothingService, times(1)).tryConsume(eq(20L), anyDouble());
    }

    @Test
    @DisplayName("when multiple campaigns match -> highest price wins")
    void evaluateBid_multipleMatchingCampaigns_selectsHighestBidder() {
        // Create two campaigns that both match "Kobler" with empty spending and large budget
        Campaign campaign1 = new Campaign("Campaign 1", Set.of("Kobler"), new BigDecimal("100.0"));
        campaign1.setId(101L);
        campaign1.setSpending(BigDecimal.ZERO);

        Campaign campaign2 = new Campaign("Campaign 2", Set.of("kobler"), new BigDecimal("100.0"));
        campaign2.setId(102L);
        campaign2.setSpending(BigDecimal.ZERO);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign1, campaign2));
        // allow smoothing for whichever winner chosen
        when(smoothingService.tryConsume(anyLong(), anyDouble())).thenReturn(true);
        // simulate successful atomic DB update for whoever is winner
        when(campaignRepository.incrementSpendingIfNotExceed(anyLong(), any(BigDecimal.class))).thenReturn(1);

        // Evaluate bid - deterministicRandom will produce different random numbers for each candidate
        var decision = biddingService.evaluateBid(1000L, Set.of("kOBLeR"));

        assertThat(decision.bid()).isTrue();

        // verify exactly one increment attempt (only winner)
        ArgumentCaptor<Long> campaignIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(campaignRepository, times(1))
                .incrementSpendingIfNotExceed(campaignIdCaptor.capture(), amountCaptor.capture());
        Long passedCampaignId = campaignIdCaptor.getValue();
        BigDecimal passedAmount = amountCaptor.getValue();



        // ensure that the winner is one of the candidates
        assertThat(List.of(101L, 102L)).contains(passedCampaignId);
        // spending must equal decision amount
        assertThat(passedAmount).isEqualByComparingTo(BigDecimal.valueOf(decision.bidAmount()));
        // ensure that the winner is one of the candidates by checking that smoothing was attempted for one id
        verify(smoothingService, times(1)).tryConsume(passedCampaignId, passedAmount.doubleValue());
    }

    @Test
    @DisplayName("Null or empty keywords should return no-bid")
    void evaluateBid_invalidKeywords_returnsNoBid() {
        // No need to set up mocks as we expect early return

        // Test null keywords
        var nullDecision = biddingService.evaluateBid(1L, null);
        assertThat(nullDecision.bid()).isFalse();

        // Test empty keywords
        var emptyDecision = biddingService.evaluateBid(1L, Set.of());
        assertThat(emptyDecision.bid()).isFalse();

        verifyNoInteractions(campaignRepository);
        verifyNoInteractions(smoothingService);
    }

    @Test
    @DisplayName("If no winner is selected, returns no bid")
    void evaluateBid_noWinner_returnsNoBid() {
        // Arrange
        when(campaignRepository.findAll()).thenReturn(List.of());

        // Act
        var bidDecision = biddingService.evaluateBid(1L, Set.of("keyword"));

        // Assert
        assertThat(bidDecision.bid()).isFalse();
        assertThat(bidDecision.bidAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Single matching campaign with negative price should return no-bid")
    void evaluateBid_singleMatchingCampaignNegativePrice_returnsNoBid() {
        // Arrange
        var random = mock(Random.class);
        biddingService = new BiddingService(campaignRepository, random, smoothingService);

        Campaign campaign = new Campaign("C1", Set.of("sports"), new BigDecimal("5.0"));
        campaign.setId(1L);
        campaign.setSpending(new BigDecimal("4.99")); // Almost spent
        when(campaignRepository.findAll()).thenReturn(List.of(campaign));
        when(random.nextDouble()).thenReturn(-2.0); // negative price

        // Act
        var decision = biddingService.evaluateBid(1L, Set.of("sports"));

        // Assert
        assertThat(decision.bid()).isFalse();
        verify(smoothingService, times(1)).tryConsume(anyLong(), anyDouble());
        verify(campaignRepository, never()).incrementSpendingIfNotExceed(anyLong(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Catch exception during atomic update and refund tokens")
    void evaluateBid_failureUpdatingCampaign_refundsTokens() {
        Campaign campaign = new Campaign("Camp", Set.of("Kobler"), new BigDecimal("100.0"));
        campaign.setId(20L);
        campaign.setSpending(BigDecimal.ZERO);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign));
        // allow smoothing reservation
        when(smoothingService.tryConsume(anyLong(), anyDouble())).thenReturn(true);
        // simulate DB error on the atomic update
        doThrow(new RuntimeException("DB failure")).when(campaignRepository).incrementSpendingIfNotExceed(eq(20L), any(BigDecimal.class));

        // Act
        var decision = biddingService.evaluateBid(1L, Set.of("Kobler"));

        // Assert
        assertThat(decision.bid()).isFalse();
        // ensure atomic update was attempted once
        verify(campaignRepository, times(1)).incrementSpendingIfNotExceed(eq(20L), any(BigDecimal.class));
        // smoothing token refunded on DB error
        verify(smoothingService, times(1)).refund(eq(20L), anyDouble());
    }

    @Test
    @DisplayName("Atomic update failed (concurrent/overspend) for campaignId, refunds tokens and tries next")
    void evaluateBid_failureUpdatingCampaign_refundsTokensAndTriesNext() {
        Campaign campaign = new Campaign("Camp", Set.of("Kobler"), new BigDecimal("100.0"));
        campaign.setId(20L);
        campaign.setSpending(BigDecimal.ZERO);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign));
        // allow smoothing reservation
        when(smoothingService.tryConsume(anyLong(), anyDouble())).thenReturn(true);
        // simulate DB error on the atomic update
        when(campaignRepository.incrementSpendingIfNotExceed(eq(20L), any(BigDecimal.class))).thenReturn(0);

        // Act
        var decision = biddingService.evaluateBid(1L, Set.of("Kobler"));

        // Assert
        assertThat(decision.bid()).isFalse();
        // ensure atomic update was attempted once
        verify(campaignRepository, times(1)).incrementSpendingIfNotExceed(eq(20L), any(BigDecimal.class));
        // smoothing token refunded on DB error
        verify(smoothingService, times(1)).refund(eq(20L), anyDouble());
    }
}
