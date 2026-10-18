package org.paron.paypalfunding.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.paron.paypalfunding.api.CreateFundingRequest;
import org.paron.paypalfunding.api.FundingResponse;
import org.paron.paypalfunding.model.FundingAttempt;
import org.paron.paypalfunding.model.FundingStatus;
import org.paron.paypalfunding.repository.FundingAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class FundingService {
    private final FundingAttemptRepository attempts;
    private final PaypalClient paypal;
    private final LedgerClient ledger;
    @Value("${paypal.currency}") private String currency;
    @Value("${paypal.maximum-amount}") private BigDecimal maximumAmount;

    @Transactional
    public FundingResponse create(CreateFundingRequest request) {
        BigDecimal amount = request.amount().setScale(2);
        if (amount.compareTo(maximumAmount) > 0) throw new IllegalArgumentException("Amount exceeds demo-wallet funding limit");
        FundingAttempt attempt = attempts.save(FundingAttempt.builder().id(UUID.randomUUID()).userId(request.userId())
                .amount(amount).currency(currency).status(FundingStatus.CREATED).build());
        JsonNode response = paypal.createOrder(amount, currency);
        String orderId = required(response, "id");
        attempt.setPaypalOrderId(orderId); attempt.setStatus(FundingStatus.PAYER_ACTION_REQUIRED);
        return FundingResponse.from(attempt, approvalUrl(response));
    }

    /** Safe to retry: the ledger rejects a second credit for the same funding ID. */
    @Transactional
    public FundingResponse capture(String paypalOrderId) {
        FundingAttempt attempt = attempts.findByPaypalOrderId(paypalOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Unknown PayPal order"));
        if (attempt.getStatus() == FundingStatus.CREDITED) return FundingResponse.from(attempt, null);
        JsonNode response = paypal.capture(paypalOrderId);
        if (!"COMPLETED".equals(response.path("status").asText())) throw new IllegalStateException("PayPal capture is not completed");
        JsonNode capture = response.at("/purchase_units/0/payments/captures/0");
        if (capture.isMissingNode() || !attempt.getAmount().setScale(2).toPlainString().equals(capture.at("/amount/value").asText())
                || !attempt.getCurrency().equals(capture.at("/amount/currency_code").asText())) {
            attempt.setStatus(FundingStatus.FAILED); throw new IllegalStateException("Captured amount or currency does not match funding attempt");
        }
        attempt.setPaypalCaptureId(required(capture, "id")); attempt.setStatus(FundingStatus.CAPTURED);
        ledger.credit(attempt.getId(), attempt.getUserId(), attempt.getAmount());
        attempt.setStatus(FundingStatus.CREDITED);
        return FundingResponse.from(attempt, null);
    }
    @Transactional(readOnly = true)
    public FundingResponse find(UUID id) { return FundingResponse.from(attempts.findById(id).orElseThrow(() -> new EntityNotFoundException("Unknown funding ID")), null); }
    private static String required(JsonNode node, String field) { String value = node.path(field).asText(); if (value.isBlank()) throw new IllegalStateException("Invalid PayPal response"); return value; }
    private static String approvalUrl(JsonNode response) { for (JsonNode link : response.path("links")) if ("approve".equals(link.path("rel").asText())) return link.path("href").asText(); return null; }
}
