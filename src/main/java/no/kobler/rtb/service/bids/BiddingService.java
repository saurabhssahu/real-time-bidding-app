package no.kobler.rtb.service.bids;

import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Service
public class BiddingService {

    private static final Logger log = LoggerFactory.getLogger(BiddingService.class);

    private final CampaignRepository campaignRepository;
    private final Random random;

    public BiddingService(CampaignRepository campaignRepository, Random random) {
        this.campaignRepository = campaignRepository;
        this.random = random;
    }

    /**
     * Evaluate a bid request.
     * <p>
     * - Find campaigns whose keywords match (trim stored keyword and incoming keyword, compare case-insensitively).
     * - For each matching campaign, generate a random price in [0, 10].
     * - Choose campaign with the highest random price.
     * - If chosen campaign has sufficient budget (spending + price <= budget), persist spending and return bid.
     * - Otherwise return no-bid.
     * <p>
     * Note: This implementation is intentionally synchronous and simple (single-instance).
     */
    @Transactional
    public BidDecision evaluateBid(long bidId, Set<String> incomingKeywords) {
        log.debug("Evaluating bidId={} for keywords={}", bidId, incomingKeywords);

        // Defensive: empty incomingKeywords -> no bid
        if (incomingKeywords == null || incomingKeywords.isEmpty()) {
            log.debug("No keywords provided for bidId={}, returning no-bid", bidId);
            return new BidDecision(false, 0.0);
        }

        List<Campaign> allCampaigns = campaignRepository.findAll();

        // Filter campaigns with at least one matching keyword (trim both sides and equalsIgnoreCase)
        List<Campaign> candidateCampaigns = allCampaigns.stream()
                .filter(campaign -> matchesAnyKeyword(campaign.getKeywords(), incomingKeywords))
                .toList();

        if (candidateCampaigns.isEmpty()) {
            log.debug("No matching campaigns for bidId={}, returning no-bid", bidId);
            return new BidDecision(false, 0.0);
        }

        // Generate random price for each candidate and pick the highest
        Campaign winner = null;
        double bestPrice = -1.0;
        for (Campaign campaign : candidateCampaigns) {
            double price = BigDecimal.valueOf(random.nextDouble() * 10.0)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            log.debug("Campaign id={} candidate price={}", campaign.getId(), price);
            if (price > bestPrice) {
                bestPrice = price;
                winner = campaign;
            }
        }

        if (winner == null) {
            log.debug("No winner selected for bidId={}, returning no-bid", bidId);
            return new BidDecision(false, 0.0);
        }

        // Check budget: spending + price <= budget
        BigDecimal currentSpending = Optional.ofNullable(winner.getSpending()).orElse(BigDecimal.ZERO);
        BigDecimal biddingPrice = BigDecimal.valueOf(bestPrice);
        BigDecimal newSpending = currentSpending.add(biddingPrice);

        if (newSpending.compareTo(winner.getBudget()) > 0) {
            log.info("Winner campaign id={} would overspend budget (spending={} + price={} > budget={}), returning no-bid",
                    winner.getId(), currentSpending, bestPrice, winner.getBudget());
            return new BidDecision(false, 0.0);
        }

        // Commit spending - simple approach for single instance
        winner.setSpending(newSpending);
        campaignRepository.save(winner);
        log.info("Placed bid for bidId={} campaignId={} amount={}", bidId, winner.getId(), bestPrice);

        return new BidDecision(true, bestPrice);
    }

    /**
     * Returns true if any of the campaignKeywords match any of the incomingKeywords.
     * Both sides are trimmed and compared using equalsIgnoreCase.
     *
     * @param campaignKeywords the set of keywords from the campaign
     * @param incomingKeywords the set of keywords from the incoming bid
     * @return true if at least one keyword matches, false otherwise
     */
    private boolean matchesAnyKeyword(Set<String> campaignKeywords, Set<String> incomingKeywords) {
        if (campaignKeywords == null || campaignKeywords.isEmpty()) return false;

//        boolean hasMatch = campaignKeywords.stream()
//                .anyMatch(campaignKeyword ->
//                        incomingKeywords.stream()
//                                .anyMatch(keyword ->
//                                        keyword.trim().equalsIgnoreCase(campaignKeyword.trim())));

        for (String ck : campaignKeywords) {
            if (ck == null) continue;
            for (String ik : incomingKeywords) {
                if (ik == null) continue;
                if (ck.trim().equalsIgnoreCase(ik.trim())) {
                    return true;
                }
            }
        }

        return false;
    }
}
