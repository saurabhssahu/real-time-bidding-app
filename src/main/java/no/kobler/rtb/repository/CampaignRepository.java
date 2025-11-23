package no.kobler.rtb.repository;


import no.kobler.rtb.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    // Basic CRUD provided by JpaRepository.

    /**
     * Atomically increment spending by `amount` only if spending + amount <= budget.
     * Returns number of rows updated (1 = success, 0 = condition failed / concurrent update).
     * <p>
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE Campaign c " +
            "SET c.spending = c.spending + :amount " +
            "WHERE c.id = :id AND (c.spending + :amount) <= c.budget",
            nativeQuery = true)
    int incrementSpendingIfNotExceed(@Param("id") Long id, @Param("amount") BigDecimal amount);

}
