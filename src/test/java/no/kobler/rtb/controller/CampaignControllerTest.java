package no.kobler.rtb.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import no.kobler.rtb.dto.CampaignRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CampaignControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createAndFetchCampaign() throws Exception {
        var campaignRequest = new CampaignRequest();
        campaignRequest.setName("Test");
        campaignRequest.setKeywords(Set.of("Kobler", "Contextual"));
        campaignRequest.setBudget(new BigDecimal("100.0"));

        var json = objectMapper.writeValueAsString(campaignRequest);

        // create
        var createResult = mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.keywords", containsInAnyOrder("Kobler", "Contextual")))
                .andExpect(jsonPath("$.budget").value(100.0))
                .andExpect(jsonPath("$.spending").value(0))
                .andReturn();

        // extract id from response
        String response = createResult.getResponse().getContentAsString();
        var node = objectMapper.readTree(response);
        long id = node.get("id").asLong();

        // fetch
        mockMvc.perform(get("/campaigns/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.keywords", containsInAnyOrder("Kobler", "Contextual")))
                .andExpect(jsonPath("$.budget").value(100.0))
                .andExpect(jsonPath("$.spending").value(0));
    }
}
