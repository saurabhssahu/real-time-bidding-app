package no.kobler.rtb.service.bids;

import no.kobler.rtb.model.Campaign;
import no.kobler.rtb.repository.CampaignRepository;
import no.kobler.rtb.smoothing.SmoothingService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Service
public class BiddingService {

    private static final Logger log = LoggerFactory.getLogger(BiddingService.class);

    private static final double MAX_BID_AMOUNT = 10.0;
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final CampaignRepository campaignRepository;
    private final Random random;
    private final SmoothingService smoothingService;

    public BiddingService(CampaignRepository campaignRepository, Random random, SmoothingService smoothingService) {
        this.campaignRepository = campaignRepository;
        this.random = random;
        this.smoothingService = smoothingService;
    }


    /**
     * Evaluates a bid with a given id and set of keywords.
     * <p>
     * The method first filters the incoming keywords to ensure they are not empty.
     * Then, it processes the filtered keywords by finding matching campaigns and evaluating the bids.
     * If no valid bid is found, it returns a {@link BidDecision} with a bid amount of 0.0 and a flag indicating no bid was found.
     * </p>
     *
     * @param bidId            the id of the bid to evaluate
     * @param incomingKeywords the set of keywords to evaluate
     * @return an Optional containing the result of the evaluation, or an empty Optional if no valid bid was found
     */
    @Transactional
    public BidDecision evaluateBid(long bidId, Set<String> incomingKeywords) {
        log.debug("Evaluating bidId={} for keywords={}", bidId, incomingKeywords);

        return Optional.ofNullable(incomingKeywords)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(keywords -> processBid(bidId, keywords))
                .orElseGet(() -> {
                    log.debug("No valid bid for bidId={}", bidId);
                    return new BidDecision(false, 0.0);
                });
    }

    private Optional<BidDecision> processBid(long bidId, Set<String> keywords) {
        return findMatchingCampaigns(keywords)
                .flatMap(campaigns -> findWinningBid(bidId, campaigns));
    }

    /**
     * Finds all campaigns that match the given set of keywords.
     * <p>
     * The method first retrieves all campaigns from the database and then filters them
     * based on whether they have any matching keywords with the given set. If no
     * matching campaigns are found, an empty Optional is returned. Otherwise, an
     * Optional containing the matching campaigns is returned.
     * </p>
     *
     * @param keywords the set of keywords to match against
     * @return an Optional containing the matching campaigns, or an empty Optional if no matching campaigns were found
     */
    private Optional<List<Campaign>> findMatchingCampaigns(Set<String> keywords) {
        List<Campaign> campaigns = campaignRepository.findAll();
        List<Campaign> matchingCampaigns = campaigns.stream()
                .filter(campaign -> hasMatchingKeyword(campaign.getKeywords(), keywords))
                .toList();

        return matchingCampaigns.isEmpty() ?
                Optional.empty() :
                Optional.of(matchingCampaigns);
    }

    private boolean hasMatchingKeyword(Set<String> campaignKeywords, Set<String> incomingKeywords) {
        if (isEmpty(campaignKeywords) || isEmpty(incomingKeywords)) {
            return false;
        }

        return campaignKeywords.stream()
                .anyMatch(campaignKeyword -> incomingKeywords.stream()
                        .anyMatch(incomingKeyword -> !StringUtils.isAllBlank(campaignKeyword, incomingKeyword) &&
                                campaignKeyword.trim().equalsIgnoreCase(incomingKeyword.trim())));
    }

    private Optional<BidDecision> findWinningBid(long bidId, List<Campaign> campaigns) {
        return createBidCandidates(campaigns)
                .filter(this::isWithinBudget)
                .filter(this::canReserveBidPrice)
                .findFirst()
                .flatMap(candidate -> finalizeBid(candidate, bidId));
    }

    /**
     * Creates a stream of bid candidates from the given list of campaigns.
     * The stream is sorted in descending order of bid price.
     * <p>
     * The method first maps each campaign to a bid candidate with a random bid price, and then sorts the resulting stream
     * in descending order of bid price.
     * </p>
     *
     * @param campaigns the list of campaigns to create bid candidates from
     * @return a stream of bid candidates sorted in descending order of bid price
     */
    private Stream<BidCandidate> createBidCandidates(List<Campaign> campaigns) {
        return campaigns.stream()
                .map(this::createBidCandidate)
                .sorted(Comparator.comparing(BidCandidate::price).reversed());
    }

    private BidCandidate createBidCandidate(Campaign campaign) {
        double price = BigDecimal.valueOf(random.nextDouble() * MAX_BID_AMOUNT)
                .setScale(SCALE, ROUNDING_MODE)
                .doubleValue();
        return new BidCandidate(campaign, price);
    }

    /**
     * Checks if the given bid candidate is within the budget of its campaign.
     * If the new spending would exceed the budget, logs an info message and returns false.
     * Otherwise, returns true.
     *
     * @param candidate the bid candidate to check
     * @return true if the candidate is within its campaign's budget, false otherwise
     */
    private boolean isWithinBudget(BidCandidate candidate) {
        Campaign candidateCampaign = candidate.campaign;
        BigDecimal currentSpending = Optional.ofNullable(candidateCampaign.getSpending()).orElse(BigDecimal.ZERO);
        BigDecimal newSpending = currentSpending.add(BigDecimal.valueOf(candidate.price));

        boolean withinBudget = newSpending.compareTo(candidateCampaign.getBudget()) <= 0;
        if (!withinBudget) {
            log.info("Campaign id={} would overspend budget (spending={} + price={} > budget={})",
                    candidateCampaign.getId(), currentSpending, candidate.price, candidateCampaign.getBudget());
        }
        return withinBudget;
    }

    /**
     * Attempt to reserve a bid price for a campaign.
     * If the reservation succeeds, the method returns true.
     * If the reservation fails, the method logs a debug message and returns false.
     *
     * @param candidate the bid candidate to reserve
     * @return true if the reservation was successful, false otherwise
     */
    private boolean canReserveBidPrice(BidCandidate candidate) {
        boolean reserved = smoothingService.tryConsume(candidate.campaign.getId(), candidate.price);
        if (!reserved) {
            log.debug("Campaign id={} failed smoothing reservation", candidate.campaign.getId());
        }
        return reserved;
    }

    /**
     * Attempts to finalize a bid for a campaign.
     * <p>
     * The method first attempts to update the campaign's spending in the database.
     * If the update is successful, it returns a BidDecision with the bid won and the price.
     * If the update fails due to a concurrent modification, the method logs a debug message and refunds the tokens `amount`.
     * If the update fails due to a DB exception, the method logs an error message and refunds the tokens `amount`.
     *
     * @param candidate the bid candidate to finalize
     * @param bidId     the id of the bid to finalize
     * @return an Optional containing a BidDecision if the bid was finalized successfully, or an empty Optional otherwise
     */
    private Optional<BidDecision> finalizeBid(BidCandidate candidate, long bidId) {
        int updatedRows;
        Campaign candidateCampaign = candidate.campaign;
        try {
            updatedRows = campaignRepository.incrementSpendingIfNotExceed(
                    candidateCampaign.getId(),
                    BigDecimal.valueOf(candidate.price)
            );
        } catch (Exception e) {
            log.error("DB update error for campaignId={} price={} : {}", candidateCampaign.getId(), candidate.price, e.getMessage());
            // Refund tokens on DB exception
            smoothingService.refund(candidateCampaign.getId(), candidate.price);
            return Optional.empty();
        }

        if (updatedRows > 0) {
            log.info("Bid won: bidId={}, campaignId={}, price={}",
                    bidId, candidateCampaign.getId(), candidate.price);
            return Optional.of(new BidDecision(true, candidate.price));
        }

        log.debug("Atomic update failed as concurrent modification detected for campaignId={}, trying next candidate",
                candidateCampaign.getId());
        smoothingService.refund(candidateCampaign.getId(), candidate.price);
        return Optional.empty();
    }

    private record BidCandidate(Campaign campaign, double price) {
    }

}
