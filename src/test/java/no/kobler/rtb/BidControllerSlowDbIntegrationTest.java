package no.kobler.rtb;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test that simulates a slow DB update (incrementSpendingIfNotExceed).
 * Controller should return 204 No Content when DB update takes too long.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BidControllerSlowDbIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CampaignRepository campaignRepository;

    @Autowired
    SmoothingService smoothingService; // keep smoothing fast in this test

    @SpyBean
    CampaignRepository spyCampaignRepository; // spy so we can delay the increment method

    @BeforeEach
    void setup() {
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("slow DB update -> controller returns 204 (no-bid) even if smoothing succeeds")
    void slowDbUpdateReturnsNoContent() throws Exception {
        // Create an active campaign
        Campaign camp = new Campaign("SlowDbCamp", Set.of(" Kobler "), new BigDecimal("100.0"));
        camp.setSpending(BigDecimal.ZERO);
        camp = campaignRepository.save(camp);

        // Ensure smoothing is fast (use real smoothingService)
        // Delay the repository method incrementSpendingIfNotExceed
        doAnswer(invocation -> {
            // Sleep longer than controller timeout to simulate slow DB update
            Thread.sleep(700);
            // call real method by using invocation.callRealMethod() is tricky in SpyBean when method is interface - but here spy is on the actual bean, so:
            return invocation.callRealMethod();
        }).when(spyCampaignRepository).incrementSpendingIfNotExceed(anyLong(), any(BigDecimal.class));

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

