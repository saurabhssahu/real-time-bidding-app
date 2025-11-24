package no.kobler.rtb.controller;

import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import no.kobler.rtb.service.BidOrchestrator;
import no.kobler.rtb.service.bids.BiddingService;
import no.kobler.rtb.smoothing.SmoothingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BidControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CampaignRepository campaignRepository;

    @SpyBean
    private BiddingService biddingService;

    @SpyBean
    private SmoothingService smoothingService;

    @BeforeEach
    void cleanup() {
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /bids -> returns 200 and updates campaign spending for matching campaign")
    void postBid_success_updatesSpending() throws Exception {
        // Prepare a campaign (simulate creation, trimming whitespace on save)
        Campaign campaign = new Campaign("TestCampaign", Set.of(" Kobler "), new BigDecimal("100.0"));
        campaign.setSpending(BigDecimal.ZERO);
        campaign = campaignRepository.save(campaign); // saved keywords should be trimmed by service or repository logic

        String bidRequest = """
                {
                  "bidId": 1,
                  "keywords": ["kobler"]
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bidId").value(1))
                .andExpect(jsonPath("$.bidAmount").isNumber());

        // Verify spending updated in DB (non-zero)
        Campaign updated = campaignRepository.findById(campaign.getId()).orElseThrow();
        // spending should be > 0 after winning the bid
        assertThat(updated.getSpending()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("POST /bids -> returns 204 when no campaign matches")
    void postBid_noMatch_returnsNoContent() throws Exception {
        Campaign campaign = new Campaign("Other", Set.of("Acme"), new BigDecimal("50.0"));
        campaignRepository.save(campaign);

        String bidRequest = """
                {
                  "bidId": 2,
                  "keywords": ["KOBLER"]
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest))
                .andExpect(status().isNoContent());

        // Ensure spending unchanged
        Campaign unchanged = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(unchanged.getSpending()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Validation tests

    @Test
    @DisplayName("POST /bids -> 400 when bidId is missing")
    void postBid_missingBidId_returnsBadRequest() throws Exception {
        String bidRequest = """
                {
                  "keywords": ["kobler"]
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /bids -> 400 when keywords is empty array")
    void postBid_emptyKeywords_returnsBadRequest() throws Exception {
        String bidRequest = """
                {
                  "bidId": 3,
                  "keywords": []
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /bids -> 400 when keywords is null")
    void postBid_nullKeywords_returnsBadRequest() throws Exception {
        String bidRequest = """
                {
                  "bidId": 4,
                  "keywords": null
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fallback: top-priced candidate overspends -> next candidate wins")
    void fallback_topCandidateOverspends_nextWins() throws Exception {
        // Create a mock Random for this test only
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextDouble())
                .thenReturn(0.9)  // First call returns 0.9
                .thenReturn(0.5); // Second call returns 0.5

        // Inject the mock Random into the BiddingService
        ReflectionTestUtils.setField(biddingService, "random", mockRandom);


        // Create two campaigns that both match "kobler"
        Campaign campaign1 = new Campaign("HighPriceButLowBudget", Set.of(" Kobler "), new BigDecimal("8.00"));
        campaign1.setSpending(BigDecimal.ZERO);
        campaign1 = campaignRepository.save(campaign1);

        // Second campaign can pay 5.00
        Campaign campaign2 = new Campaign("LowerPriceSufficientBudget", Set.of(" Kobler "), new BigDecimal("100.00"));
        campaign2.setSpending(BigDecimal.ZERO);
        campaign2 = campaignRepository.save(campaign2);

        // Build request (both campaigns match the keyword)
        String req = """
                {
                  "bidId": 201,
                  "keywords": ["kobler"]
                }
                """;

        // First campaign has small budget so it cannot pay 9.00
        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidId").value(201))
                .andExpect(jsonPath("$.bidAmount").value(5.0)); // should be the second candidate's price

        // Verify spending updated on the second campaign, not the first
        Campaign updated1 = campaignRepository.findById(campaign1.getId()).orElseThrow();
        Campaign updated2 = campaignRepository.findById(campaign2.getId()).orElseThrow();

        // first campaign unchanged (skipped due to budget)
        assertThat(updated1.getSpending()).isEqualByComparingTo(BigDecimal.ZERO);

        // second campaign spent 5.00
        assertThat(updated2.getSpending()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("POST /bids -> returns 204 when orchestrator times out")
    void when_orchestrator_times_out_controller_returns_204() throws Exception {

        var mockOrchestrator = mock(BidOrchestrator.class);
        // simulate timeout -> orchestrator returns Optional.empty()
        when(mockOrchestrator.evaluateWithDefaultTimeout(anyLong(), anySet()))
                .thenReturn(Optional.empty());

        String bidRequest = """
                {
                  "bidId": 1,
                  "keywords": ["kobler"]
                }
                """;
        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest))
                .andExpect(status().isNoContent());
    }
}
