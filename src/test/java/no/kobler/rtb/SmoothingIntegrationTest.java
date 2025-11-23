package no.kobler.rtb;


import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SmoothingIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CampaignRepository campaignRepository;

    @MockBean
    private Random random;

    @BeforeEach
    void cleanup() {
        // This will return 0.4 for the first 4 calls, then 0.0
        when(random.nextDouble())
                .thenReturn(0.4)
                .thenReturn(0.4)
                .thenReturn(0.4)
                .thenReturn(0.4);

        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("smoothing cap enforced: third rapid bid denied")
    void smoothingCap_thirdBidDenied() throws Exception {
        // Create campaign with trimmed keyword (we keep Option A: trim on save)
        Campaign campaign = new Campaign("SmoothingCamp", Set.of(" Kobler "), new BigDecimal("100.0"));
        campaign.setSpending(BigDecimal.ZERO);
        campaign = campaignRepository.save(campaign);

        // First bid -> expected 200 (price 4.0)
        String bidRequest1 = """
                {
                  "bidId": 1,
                  "keywords": ["kobler"]
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidId").value(1))
                .andExpect(jsonPath("$.bidAmount").value(4.0));

        // Second bid -> expected 200 (price 4.0), total spending 8.0
        String bidRequest2 = """
                {
                  "bidId": 2,
                  "keywords": ["kobler"]
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidId").value(2))
                .andExpect(jsonPath("$.bidAmount").value(4.0));

        // Third bid -> expected 204 (would exceed smoothing cap 10 NOK)
        String bidRequest3 = """
                {
                  "bidId": 3,
                  "keywords": ["kobler"]
                }
                """;

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequest3))
                .andExpect(status().isNoContent());

        // Verify spending in DB equals 8.0
        Campaign updated = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(updated.getSpending()).isEqualByComparingTo(new BigDecimal("8.0"));
    }
}
