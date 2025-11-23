package no.kobler.rtb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class BidRequest {

    @NotNull(message = "BidId cannot be null")
    private Long bidId;

    @NotNull(message = "Keywords cannot be null")
    @Size(min = 1, message = "At least one keyword is required")
    private Set<@NotBlank(message = "Keyword cannot be blank") String> keywords;

    public BidRequest() {
    }

    public BidRequest(Long bidId, Set<String> keywords) {
        this.bidId = bidId;
        this.keywords = keywords;
    }

    public Long getBidId() {
        return bidId;
    }

    public void setBidId(Long bidId) {
        this.bidId = bidId;
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(Set<String> keywords) {
        this.keywords = keywords;
    }
}
