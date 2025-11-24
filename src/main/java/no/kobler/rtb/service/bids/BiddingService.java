package no.kobler.rtb.service.bids;

import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import no.kobler.rtb.smoothing.SmoothingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Service
public class BiddingService {

    private static final Logger log = LoggerFactory.getLogger(BiddingService.class);

    private final CampaignRepository campaignRepository;
    private final Random random;
    private final SmoothingService smoothingService;

    public BiddingService(CampaignRepository campaignRepository, Random random, SmoothingService smoothingService) {
        this.campaignRepository = campaignRepository;
        this.random = random;
        this.smoothingService = smoothingService;
    }


    /**
     * Evaluate bid with fallback and atomic DB update.
     * <p>
     * Behavior: we generate prices for all matching campaigns, sort by price descending,
     *   and attempt each candidate in order (fallback) until one succeeds.
     *   For persistence, we use an atomic conditional DB update (incrementSpendingIfNotExceed).
     *   We reserve smoothing tokens before attempting DB update and refund if DB update fails.
     * </p>
     *
     * @param bidId the id of the bid to evaluate
     * @param incomingKeywords the set of keywords from the incoming bid
     * @return a BidDecision with the bid result (bid, amount)
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
        List<Campaign> candidateCampaigns = getCandidateCampaigns(allCampaigns, incomingKeywords);

        if (candidateCampaigns.isEmpty()) {
            log.debug("No matching campaigns for bidId={}, returning no-bid", bidId);
            return new BidDecision(false, 0.0);
        }

        List<CandidatePrice> candidatePrices = buildCandidatePrices(candidateCampaigns);

        Optional<BidDecision> winner = attemptToPlaceBid(bidId, candidatePrices);

        return winner.orElseGet(() -> {
            log.debug("All candidates exhausted for bidId={}, returning no-bid", bidId);
            return new BidDecision(false, 0.0);
        });
    }

    public record CandidatePrice(Campaign campaign, double price) {
    }


    /**
     * Filters a list of campaigns based on whether any of their keywords match any of the incomingKeywords.
     *
     * @param allCampaigns     the list of campaigns to filter
     * @param incomingKeywords the set of keywords to match against
     * @return a list of campaigns that have at least one matching keyword
     */
    private List<Campaign> getCandidateCampaigns(List<Campaign> allCampaigns, Set<String> incomingKeywords) {
        return allCampaigns.stream()
                .filter(campaign -> matchesAnyKeyword(campaign.getKeywords(), incomingKeywords))
                .toList();
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

    /**
     * Builds a list of CandidatePrice objects from a list of campaigns.
     * <p>
     * Each campaign is given a random bidding price between 0.0 and 10.0
     * (inclusive). The prices are then sorted in descending order.
     *
     * @param candidateCampaigns the list of campaigns to build prices for
     * @return a list of CandidatePrice objects, sorted by price in descending order
     */
    private List<CandidatePrice> buildCandidatePrices(List<Campaign> candidateCampaigns) {
        List<CandidatePrice> candidatePrices = new ArrayList<>();
        for (Campaign campaign : candidateCampaigns) {
            double biddingPrice = BigDecimal.valueOf(random.nextDouble() * 10.0)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            candidatePrices.add(new CandidatePrice(campaign, biddingPrice));
        }
        candidatePrices.sort(Comparator.comparingDouble(CandidatePrice::price).reversed());
        return candidatePrices;
    }


    /**
     * Attempt to place a bid for the given bidId using the provided list of candidate prices.
     * <p>
     * The method iterates over the candidate prices in descending order of price and attempts to
     * reserve smoothing tokens `amount` for the campaign. If the campaign would overspend its budget, the
     * next candidate is tried. If the campaign fails to reserve amount, the next candidate
     * is tried. If the campaign fails to update its spending due to a concurrent update or
     * overspending, the method refunds the amount and tries the next candidate.
     * <p>
     * If a bid is successfully placed, the method returns an Optional containing a BidDecision with the
     * bid result (bidId, amount). If no bid can be placed, the method returns an empty Optional.
     *
     * @param bidId           the id of the bid to place
     * @param candidatePrices the list of candidate prices to try
     * @return an Optional containing a BidDecision with the bid result (bidId, amount) if successful, or an empty Optional if not
     */
    private Optional<BidDecision> attemptToPlaceBid(long bidId, List<CandidatePrice> candidatePrices) {
        for (CandidatePrice cp : candidatePrices) {
            Campaign campaign = cp.campaign();
            double price = cp.price();
            log.debug("Trying candidate id={} price={}", campaign.getId(), price);

            // Quick budget check before attempting smoothing/reservation
            BigDecimal currentSpending = Optional.ofNullable(campaign.getSpending()).orElse(BigDecimal.ZERO);
            BigDecimal biddingPrice = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
            BigDecimal newSpending = currentSpending.add(biddingPrice);
            if (newSpending.compareTo(campaign.getBudget()) > 0) {
                log.info("Candidate campaign id={} would overspend budget (spending={} + price={} > budget={}), skipping", campaign.getId(), currentSpending, price, campaign.getBudget());
                continue; // try next candidate
            }

            // Reserve smoothing tokens
            boolean reserved = smoothingService.tryConsume(campaign.getId(), price);
            if (!reserved) {
                log.info("Candidate campaign id={} failed smoothing reservation, skipping", campaign.getId());
                continue; // try next candidate
            }

            // Attempt atomic DB update: increment spending only if it still fits budget
            int updatedRows = 0;
            try {
                updatedRows = campaignRepository.incrementSpendingIfNotExceed(campaign.getId(), biddingPrice);
            } catch (Exception e) {
                log.error("DB update error for campaignId={} price={} : {}", campaign.getId(), price, e.getMessage());
                // Refund tokens on DB exception
                smoothingService.refund(campaign.getId(), price);
                continue; // try next candidate
            }

            if (updatedRows == 1) {
                // success — we can return bid.
                campaign.setSpending(newSpending);
                log.info("Placed bid for bidId={} campaignId={} amount={}", bidId, campaign.getId(), price);
                return Optional.of(new BidDecision(true, price));
            } else {
                // somebody else updated or budget no longer sufficient; refund smoothing and try next
                log.info("Atomic update failed (concurrent/overspend) for campaignId={}, refunding tokens and trying next", campaign.getId());
                smoothingService.refund(campaign.getId(), price);
                // Continue iterating
            }
        }
        return Optional.empty();
    }
}
