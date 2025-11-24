package no.kobler.rtb;


import com.fasterxml.jackson.databind.ObjectMapper;
import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import no.kobler.rtb.smoothing.SmoothingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test that simulates a slow smoothing.tryConsume (token reservation).
 * Controller should return 204 No Content when smoothing is too slow.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BidControllerSlowSmoothingIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CampaignRepository campaignRepository;

    @SpyBean
    SmoothingService smoothingService; // spy the real bean and delay tryConsume

    @BeforeEach
    void setup() {
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("slow smoothing -> controller returns 204 (no-bid)")
    void slowSmoothingReturnsNoContent() throws Exception {
        // Create a campaign that matches "kobler"
        Campaign campaign = new Campaign("SlowSmoothingCamp", Set.of(" Kobler "), new BigDecimal("100.0"));
        campaign.setSpending(BigDecimal.ZERO);
        campaign = campaignRepository.save(campaign);

        // Make smoothing.tryConsume sleep (simulate slowness) and then return true
        doAnswer(invocation -> {
            Thread.sleep(700); // longer than controller timeout (500ms)
            return true;
        }).when(smoothingService).tryConsume(anyLong(), anyDouble());

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

