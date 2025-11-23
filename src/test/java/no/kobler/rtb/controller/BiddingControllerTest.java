package no.kobler.rtb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
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
class BiddingControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CampaignRepository campaignRepository;

    @Test
    @DisplayName("POST /bids -> returns 200 and updates campaign spending for matching campaign")
    void postBid_success_updatesSpending() throws Exception {
        // Prepare a campaign (simulate creation, trimming whitespace on save)
        Campaign campaign = new Campaign("TestCampaign", Set.of(" Kobler "), new BigDecimal("100.0"));
        campaign.setSpending(BigDecimal.ZERO);
        campaign = campaignRepository.save(campaign); // saved keywords should be trimmed by service or repository logic

        var bidRequest = mapper.createObjectNode();
        bidRequest.put("bidId", 1);
        bidRequest.putArray("keywords").add("kobler");

        var mvcResult = mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bidRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bidId").value(1))
                .andExpect(jsonPath("$.bidAmount").isNumber())
                .andReturn();

        // Verify spending updated in DB (non-zero)
        Campaign updated = campaignRepository.findById(campaign.getId()).orElseThrow();
        // spending should be > 0 after winning the bid
        assertThat(updated.getSpending()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("POST /bids -> returns 204 when no campaign matches")
    void postBid_noMatch_returnsNoContent() throws Exception {
        // Ensure repository empty or has non-matching campaign
        campaignRepository.deleteAll();

        Campaign campaign = new Campaign("Other", Set.of("Acme"), new BigDecimal("50.0"));
        campaignRepository.save(campaign);

        var bidRequest = mapper.createObjectNode();
        bidRequest.put("bidId", 2);
        bidRequest.putArray("keywords").add("KOBLER");

        mvc.perform(post("/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bidRequest)))
                .andExpect(status().isNoContent());

        // Ensure spending unchanged
        Campaign unchanged = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(unchanged.getSpending()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

