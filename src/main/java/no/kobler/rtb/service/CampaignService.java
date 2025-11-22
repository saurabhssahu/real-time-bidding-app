package no.kobler.rtb.service;


import no.kobler.rtb.dto.CampaignRequest;
import no.kobler.rtb.dto.CampaignResponse;
import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Transactional
    public CampaignResponse createCampaign(CampaignRequest campaignRequest) {
        log.info("Creating campaign with name='{}'", campaignRequest.getName());

        // Normalize keywords  (trim) " Kobler " -> "Kobler"
        var keywords = campaignRequest.getKeywords().stream()
                .map(String::trim)
                .collect(Collectors.toSet());

        var campaign = new Campaign(campaignRequest.getName(), keywords, campaignRequest.getBudget());
        var saved = campaignRepository.save(campaign);

        log.info("Campaign {} created successfully with id={}", saved.getName(), saved.getId());
        return toCampaignResponse(saved);
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(Long id) {
        log.debug("Fetching campaign by id={}", id);

        var campaign = campaignRepository.findById(id).orElseThrow(() -> {
            log.warn("Campaign not found for id={}", id);
            return new IllegalArgumentException("Campaign not found");
        });

        return toCampaignResponse(campaign);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> listCampaigns() {
        log.debug("Fetching list of all campaigns");

        var campaignList = campaignRepository.findAll();

        log.info("Total campaigns found={}", campaignList.size());
        return campaignList.stream().map(this::toCampaignResponse).toList();
    }

    /**
     * Maps a Campaign object to a CampaignResponse object.
     *
     * @param campaign the Campaign object to map
     * @return a CampaignResponse object containing the data from the Campaign object
     */
    private CampaignResponse toCampaignResponse(Campaign campaign) {
        return new CampaignResponse(campaign.getId(), campaign.getName(), campaign.getKeywords(), campaign.getBudget(), campaign.getSpending());
    }
}
