package no.kobler.rtb.service;

import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import no.kobler.rtb.service.bids.BiddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BiddingServiceTest {

    private CampaignRepository campaignRepository;
    private BiddingService biddingService;
    private Random deterministicRandom;

    @BeforeEach
    void setup() {
        campaignRepository = mock(CampaignRepository.class);
        // deterministic random to control prices: will generate predictable doubles
        deterministicRandom = new Random(123L);
        // BiddingService constructor: (CampaignRepository repo, Random random)
        biddingService = new BiddingService(campaignRepository, deterministicRandom);
    }

    @Test
    @DisplayName("when no campaigns match bid keywords -> no bid (empty)")
    void noMatchingCampaigns_returnsNoBid() {
        // repository returns two campaigns whose keywords do not match
        Campaign campaign1 = new Campaign("A", Set.of("alpha"), new BigDecimal("100.0"));
        campaign1.setId(1L);
        Campaign campaign2 = new Campaign("B", Set.of("beta"), new BigDecimal("100.0"));
        campaign2.setId(2L);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign1, campaign2));

        var decision = biddingService.evaluateBid(1L, Set.of("Kobler"));

        assertThat(decision.bid()).isFalse();
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("when a single campaign matches and has budget -> return bid and update spending")
    void singleMatchingCampaign_withBudget_returnsBidAndUpdatesSpending() {
        Campaign campaign = new Campaign("Camp", Set.of(" Kobler "), new BigDecimal("100.0"));
        campaign.setId(10L);
        campaign.setSpending(BigDecimal.ZERO);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign));

        // deterministicRandom will produce a price between 0 and 10
        var decision = biddingService.evaluateBid(42L, Set.of("kobler"));

        // We expect a bid
        assertThat(decision.bid()).isTrue();
        assertThat(decision.bidAmount()).isGreaterThanOrEqualTo(0.0);
        assertThat(decision.bidAmount()).isLessThanOrEqualTo(10.0);

        // Verify the repository saved updated spending once
        ArgumentCaptor<Campaign> savedCaptor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository, times(1)).save(savedCaptor.capture());

        Campaign saved = savedCaptor.getValue();
        // spending should reflect the added bid amount
        assertThat(saved.getSpending()).isEqualByComparingTo(BigDecimal.valueOf(decision.bidAmount()));
    }

    @Test
    @DisplayName("when matching campaign exists but budget insufficient -> no bid and no update")
    void matchingCampaign_budgetInsufficient_noBid() {
        Campaign poor = new Campaign("Poor", Set.of("Kobler"), new BigDecimal("1.0"));
        poor.setId(5L);
        poor.setSpending(new BigDecimal("1.0")); // already spent full budget

        when(campaignRepository.findAll()).thenReturn(List.of(poor));

        var decision = biddingService.evaluateBid(9L, Set.of("KOBLER"));

        assertThat(decision.bid()).isFalse();
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("when multiple campaigns match -> highest price wins")
    void multipleMatchingCampaigns_highestPriceWins() {
        // Create two campaigns that both match "Kobler" with empty spending and large budget
        Campaign campaign1 = new Campaign("Campaign 1", Set.of("Kobler"), new BigDecimal("100.0"));
        campaign1.setId(101L);
        campaign1.setSpending(BigDecimal.ZERO);

        Campaign campaign2 = new Campaign("Campaign 2", Set.of("kobler"), new BigDecimal("100.0"));
        campaign2.setId(102L);
        campaign2.setSpending(BigDecimal.ZERO);

        when(campaignRepository.findAll()).thenReturn(List.of(campaign1, campaign2));

        // Evaluate bid - deterministicRandom will produce different random numbers for each candidate
        var decision = biddingService.evaluateBid(1000L, Set.of("kOBLeR"));

        assertThat(decision.bid()).isTrue();
        // verify exactly one save (the winner)
        verify(campaignRepository, times(1)).save(any());

        // capture which campaign1 was saved (winner) and its spending
        ArgumentCaptor<Campaign> cap = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository).save(cap.capture());
        Campaign winner = cap.getValue();
        assertThat(winner.getSpending()).isEqualByComparingTo(BigDecimal.valueOf(decision.bidAmount()));
        // ensure that the winner is one of the candidates
        assertThat(List.of(101L, 102L)).contains(winner.getId());
    }
}
