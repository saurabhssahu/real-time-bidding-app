package no.kobler.rtb.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import no.kobler.rtb.dto.CampaignRequest;
import org.junit.jupiter.api.DisplayName;
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
class CampaignControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /campaigns should create and fetch campaign")
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

    @Test
    @DisplayName("POST /campaigns with blank name should return 400")
    void createCampaign_blankName_shouldFail() throws Exception {
        CampaignRequest request = new CampaignRequest();
        request.setName("  "); // Blank name
        request.setKeywords(Set.of("test"));
        request.setBudget(new BigDecimal("100.0"));

        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /campaigns with empty keywords should return 400")
    void createCampaign_emptyKeywords_shouldFail() throws Exception {
        CampaignRequest request = new CampaignRequest();
        request.setName("Test Campaign");
        request.setKeywords(Set.of()); // Empty set
        request.setBudget(new BigDecimal("100.0"));

        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /campaigns with null keywords should return 400")
    void createCampaign_nullKeywords_shouldFail() throws Exception {
        String json = """
                {
                    "name": "Test Campaign",
                    "keywords": null,
                    "budget": 100.0
                }
                """;

        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /campaigns with blank keyword should return 400")
    void createCampaign_blankKeyword_shouldFail() throws Exception {
        CampaignRequest request = new CampaignRequest();
        request.setName("Test Campaign");
        request.setKeywords(Set.of("  ")); // Blank keyword
        request.setBudget(new BigDecimal("100.0"));

        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /campaigns with null budget should return 400")
    void createCampaign_nullBudget_shouldFail() throws Exception {
        String json = """
                {
                    "name": "Test Campaign",
                    "keywords": ["test"],
                    "budget": null
                }
                """;

        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /campaigns with zero budget should return 400")
    void createCampaign_zeroBudget_shouldFail() throws Exception {
        CampaignRequest request = new CampaignRequest();
        request.setName("Test Campaign");
        request.setKeywords(Set.of("test"));
        request.setBudget(BigDecimal.ZERO);

        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /campaigns with negative budget should return 400")
    void createCampaign_negativeBudget_shouldFail() throws Exception {
        CampaignRequest request = new CampaignRequest();
        request.setName("Test Campaign");
        request.setKeywords(Set.of("test"));
        request.setBudget(new BigDecimal("-100.0"));

        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
