package no.kobler.rtb.controller;

import jakarta.validation.Valid;
import no.kobler.rtb.dto.CampaignRequest;
import no.kobler.rtb.dto.CampaignResponse;
import no.kobler.rtb.service.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CampaignRequest campaignRequest) {
        log.info("Received request to create campaign '{}'", campaignRequest.getName());

        var campaignResponse = campaignService.createCampaign(campaignRequest);
        URI location = URI.create("/campaigns/" + campaignResponse.getId());

        return ResponseEntity.created(location).body(campaignResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable Long id) {
        log.info("Received request to fetch campaign id={}", id);

        try {
            var campaignResponse = campaignService.getCampaign(id);
            return ResponseEntity.ok(campaignResponse);
        } catch (IllegalArgumentException e) {
            log.warn("Campaign id={} not found", id);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getCampaigns() {
        log.info("Received request to list all campaigns");
        return ResponseEntity.ok(campaignService.listCampaigns());
    }
}
