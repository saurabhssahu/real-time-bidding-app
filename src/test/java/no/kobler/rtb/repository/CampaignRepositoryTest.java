package no.kobler.rtb.repository;


import no.kobler.rtb.model.Campaign;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CampaignRepositoryTest {

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("save and findById - should persist and return campaign")
    void saveAndFindById() {
        Campaign campaign = new Campaign("RepoTest", Set.of("k1", "k2"), new BigDecimal("150.0"));
        Campaign saved = campaignRepository.save(campaign);

        assertThat(saved.getId()).isNotNull();
        Optional<Campaign> loaded = campaignRepository.findById(saved.getId());
        assertThat(loaded).isPresent();

        Campaign found = loaded.get();
        assertThat(found.getName()).isEqualTo("RepoTest");
        assertThat(found.getBudget()).isEqualByComparingTo(new BigDecimal("150.0"));
        assertThat(found.getSpending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(found.getKeywords()).containsExactlyInAnyOrder("k1", "k2");
    }

    @Test
    @DisplayName("findAll - should return multiple persisted campaigns")
    void findAll_multiple() {
        Campaign campaignA = new Campaign("A", Set.of("x"), new BigDecimal("10.0"));
        Campaign campaignB = new Campaign("B", Set.of("y"), new BigDecimal("20.0"));

        campaignRepository.save(campaignA);
        campaignRepository.save(campaignB);

        List<Campaign> all = campaignRepository.findAll();
        assertThat(all).hasSize(2)
                .extracting(Campaign::getName)
                .containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("update spending - persist spending changes")
    void updateSpending_persisted() {
        Campaign campaign = new Campaign("UpdateTest", Set.of("u"), new BigDecimal("50.0"));

        // Save and detach
        Campaign saved = campaignRepository.saveAndFlush(campaign);
        entityManager.detach(saved);  // Detach to ensure we're getting a fresh copy

        // Update spending in a new transaction
        Campaign toUpdate = campaignRepository.findById(saved.getId()).orElseThrow();
        toUpdate.setSpending(new BigDecimal("15.5"));
        campaignRepository.saveAndFlush(toUpdate);

        // Verify
        Campaign reloaded = campaignRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSpending()).isEqualByComparingTo(new BigDecimal("15.5"));
    }

    @Test
    @DisplayName("findById - absent returns empty optional")
    void findById_absent() {
        Optional<Campaign> maybe = campaignRepository.findById(-999L);
        assertThat(maybe).isNotPresent();
    }
}
