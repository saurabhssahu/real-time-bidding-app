package no.kobler.rtb.service.bids;

import no.kobler.rtb.repository.CampaignRepository;

import java.util.Random;
import java.util.Set;

public class BiddingService {
    private final CampaignRepository repository;
    private final Random random;

    public BiddingService(CampaignRepository repository, Random random) {
        this.repository = repository;
        this.random = random;
    }

    public BidDecision evaluateBid(long bidId, Set<String> keywords) {
        // TDD: leave unimplemented (tests will fail).
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
