package no.kobler.rtb.service;


import no.kobler.rtb.dto.CampaignRequest;
import no.kobler.rtb.dto.CampaignResponse;
import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private CampaignService campaignService;

    @Test
    @DisplayName("createCampaign - should normalize keywords, save and return mapped response")
    void createCampaign_shouldNormalizeKeywordsAndSave() {
        // Arrange
        var campaignRequest = new CampaignRequest();
        campaignRequest.setName(" My Campaign ");
        campaignRequest.setKeywords(Set.of(" Kobler ", "Contextual", "KOBLER"));
        campaignRequest.setBudget(new BigDecimal("500.0"));

        // Capture what repository.save receives and return a Campaign with id assigned
        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        when(campaignRepository.save(captor.capture())).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        // Act
        CampaignResponse campaignResponse = campaignService.createCampaign(campaignRequest);

        // Assert - mapping correctness & normalization
        assertThat(campaignResponse).isNotNull();
        assertThat(campaignResponse.getId()).isEqualTo(42L);
        assertThat(campaignResponse.getName()).isEqualTo(" My Campaign ");
        // keywords should be trimmed
        assertThat(campaignResponse.getKeywords()).containsExactlyInAnyOrder("Contextual", "KOBLER", "Kobler");
        assertThat(campaignResponse.getBudget()).isEqualByComparingTo(new BigDecimal("500.0"));
        assertThat(campaignResponse.getSpending()).isEqualByComparingTo(BigDecimal.ZERO);

        // Verify repository.save called once with normalized keywords
        Campaign saved = captor.getValue();
        assertThat(saved.getKeywords()).containsExactlyInAnyOrder("Contextual", "KOBLER", "Kobler");
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    @DisplayName("getCampaign - found")
    void getCampaign_found() {
        // Arrange
        Campaign campaign = new Campaign("X", Set.of("k"), new BigDecimal("100.0"));
        campaign.setId(10L);
        campaign.setSpending(new BigDecimal("12.34"));
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));

        // Act
        CampaignResponse campaignResponse = campaignService.getCampaign(10L);

        // Assert
        assertThat(campaignResponse).isNotNull();
        assertThat(campaignResponse.getId()).isEqualTo(10L);
        assertThat(campaignResponse.getName()).isEqualTo("X");
        assertThat(campaignResponse.getBudget()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(campaignResponse.getSpending()).isEqualByComparingTo(new BigDecimal("12.34"));
        verify(campaignRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("getCampaign - not found should throw IllegalArgumentException")
    void getCampaign_notFound() {
        // Arrange
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> campaignService.getCampaign(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Campaign not found");

        verify(campaignRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("listCampaigns - empty returns empty list")
    void listCampaigns_empty() {
        when(campaignRepository.findAll()).thenReturn(List.of());

        List<CampaignResponse> list = campaignService.listCampaigns();
        assertThat(list).isNotNull().isEmpty();
        verify(campaignRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("listCampaigns - non-empty maps correctly")
    void listCampaigns_nonEmpty() {
        Campaign campaign1 = new Campaign("A", Set.of("a"), new BigDecimal("10"));
        campaign1.setId(1L);
        campaign1.setSpending(new BigDecimal("1.0"));

        Campaign campaign2 = new Campaign("B", Set.of("b"), new BigDecimal("20"));
        campaign2.setId(2L);
        campaign2.setSpending(new BigDecimal("2.0"));

        when(campaignRepository.findAll()).thenReturn(List.of(campaign1, campaign2));

        List<CampaignResponse> list = campaignService.listCampaigns();
        assertThat(list).hasSize(2);

        // verify mapping for each
        assertThat(list).extracting(CampaignResponse::getId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(list).extracting(CampaignResponse::getName).containsExactlyInAnyOrder("A", "B");
        verify(campaignRepository, times(1)).findAll();
    }
}
