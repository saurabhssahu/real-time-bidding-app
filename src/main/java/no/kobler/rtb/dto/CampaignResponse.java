package no.kobler.rtb.dto;

import java.math.BigDecimal;
import java.util.Set;

public class CampaignResponse {
    private Long id;
    private String name;
    private Set<String> keywords;
    private BigDecimal budget;
    private BigDecimal spending;

    public CampaignResponse() {
    }

    public CampaignResponse(Long id, String name, Set<String> keywords, BigDecimal budget, BigDecimal spending) {
        this.id = id;
        this.name = name;
        this.keywords = keywords;
        this.budget = budget;
        this.spending = spending;
    }

    // getters & setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(Set<String> keywords) {
        this.keywords = keywords;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public BigDecimal getSpending() {
        return spending;
    }

    public void setSpending(BigDecimal spending) {
        this.spending = spending;
    }
}
