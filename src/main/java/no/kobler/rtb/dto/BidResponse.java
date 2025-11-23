package no.kobler.rtb.dto;

public class BidResponse {

    private Long bidId;
    private double bidAmount;

    public BidResponse() {
    }

    public BidResponse(Long bidId, double bidAmount) {
        this.bidId = bidId;
        this.bidAmount = bidAmount;
    }

    public Long getBidId() {
        return bidId;
    }

    public void setBidId(Long bidId) {
        this.bidId = bidId;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(double bidAmount) {
        this.bidAmount = bidAmount;
    }
}
