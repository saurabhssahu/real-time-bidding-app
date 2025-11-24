package no.kobler.rtb.controller;

import jakarta.validation.Valid;
import no.kobler.rtb.dto.BidRequest;
import no.kobler.rtb.dto.BidResponse;
import no.kobler.rtb.service.BidOrchestrator;
import no.kobler.rtb.service.bids.BidDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/bids")
public class BidController {

    private static final Logger log = LoggerFactory.getLogger(BidController.class);

    private final BidOrchestrator bidOrchestrator;

    public BidController(BidOrchestrator bidOrchestrator) {
        this.bidOrchestrator = bidOrchestrator;
    }

    @PostMapping
    public ResponseEntity<?> handleBid(@Valid @RequestBody BidRequest bidRequest) {
        // initial validation handled by @Valid
        log.info("Received bid request id={} keywords={}", bidRequest.getBidId(), bidRequest.getKeywords());

        Optional<BidDecision> optionalBidDecision =
                bidOrchestrator.evaluateWithDefaultTimeout(bidRequest.getBidId(), Set.copyOf(bidRequest.getKeywords()));

        if (optionalBidDecision.isEmpty() || !optionalBidDecision.get().bid()) {
            // timeout or error -> respond no-bid (204)
            log.debug("No bid for id={} (timeout or error)", bidRequest.getBidId());
            return ResponseEntity.noContent().build();
        }

        BidDecision bidDecision = optionalBidDecision.get();

        BidResponse bidResponse = new BidResponse(bidRequest.getBidId(), bidDecision.bidAmount());
        log.info("Responding with bid for id={} amount={}", bidRequest.getBidId(), bidDecision.bidAmount());
        return ResponseEntity.ok(bidResponse);
    }
}
