package org.paron.paypalfunding.api;
import org.paron.paypalfunding.model.FundingAttempt;
import org.paron.paypalfunding.model.FundingStatus;
import java.math.BigDecimal;
import java.util.UUID;
public record FundingResponse(UUID fundingId, String paypalOrderId, String approvalUrl, BigDecimal amount,
                              String currency, FundingStatus status) {
    public static FundingResponse from(FundingAttempt attempt, String approvalUrl) {
        return new FundingResponse(attempt.getId(), attempt.getPaypalOrderId(), approvalUrl,
                attempt.getAmount(), attempt.getCurrency(), attempt.getStatus());
    }
}
