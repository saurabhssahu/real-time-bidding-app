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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// Enable Redis smoothing for this test class (starts embedded Redis via RedisEmbeddedConfig)
@TestPropertySource(properties = {
        "smoothing.type=redis",
        "spring.redis.host=localhost",
        "spring.redis.port=6379"
})
class RedisSmoothingIntegrationTest {

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
                .thenReturn(0.4);

        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("Redis smoothing: third rapid bid denied (embedded redis)")
    void redisSmoothing_thirdRapidBidDenied() throws Exception {
        // Create campaign
        Campaign campaign = new Campaign("RedisCamp", Set.of(" Kobler "), new BigDecimal("100.0"));
        campaign.setSpending(BigDecimal.ZERO);
        campaign = campaignRepository.save(campaign);

        String req = """
                {
                  "bidId": 9001,
                  "keywords": ["kobler"]
                }
                """;

        // Send multiple concurrent/sequential bids quickly
        mvc.perform(post("/bids").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(post("/bids").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(post("/bids").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().is(204)); // third should be denied given deterministic pricing in other tests

        // Verify spending is <= 10.00 NOK
        Campaign updated = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(updated.getSpending().doubleValue()).isLessThanOrEqualTo(10.0);
    }
}

