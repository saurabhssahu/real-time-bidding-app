package no.kobler.rtb.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public class CampaignRequest {

    @NotBlank(message = "Name cannot be blank")
    private String name;

    @NotNull(message = "Keywords cannot be null")
    @Size(min = 1, message = "At least one keyword is required")
    private Set<@NotBlank(message = "Keyword cannot be blank") String> keywords;

    @NotNull(message = "Budget cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Budget must be greater than zero")
    private BigDecimal budget;

    public CampaignRequest() {
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
}

