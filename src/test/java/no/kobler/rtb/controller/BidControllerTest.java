package no.kobler.rtb.controller;

import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BidControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CampaignRepository campaignRepository;

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
}

