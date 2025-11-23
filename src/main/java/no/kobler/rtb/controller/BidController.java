package no.kobler.rtb.controller;

import jakarta.validation.Valid;
import no.kobler.rtb.dto.BidRequest;
import no.kobler.rtb.dto.BidResponse;
import no.kobler.rtb.service.bids.BidDecision;
import no.kobler.rtb.service.bids.BiddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/bids")
public class BidController {

    private static final Logger log = LoggerFactory.getLogger(BidController.class);

    private final BiddingService biddingService;

    public BidController(BiddingService biddingService) {
        this.biddingService = biddingService;
    }

    @PostMapping
    public ResponseEntity<?> handleBid(@Valid @RequestBody BidRequest bidRequest) {
        log.info("Received bid request id={} keywords={}", bidRequest.getBidId(), bidRequest.getKeywords());
        BidDecision decision = biddingService.evaluateBid(bidRequest.getBidId(), bidRequest.getKeywords());

        if (!decision.bid()) {
            log.debug("No bid for id={}", bidRequest.getBidId());
            return ResponseEntity.noContent().build();
        }

        BidResponse bidResponse = new BidResponse(bidRequest.getBidId(), decision.bidAmount());
        log.info("Responding with bid for id={} amount={}", bidRequest.getBidId(), decision.bidAmount());
        return ResponseEntity.ok(bidResponse);
    }
}
