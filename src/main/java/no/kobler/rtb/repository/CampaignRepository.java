package no.kobler.rtb.repository;


import no.kobler.rtb.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    // Basic CRUD provided by JpaRepository.
    // Matching queries will be added later when bidding is implemented.
}
