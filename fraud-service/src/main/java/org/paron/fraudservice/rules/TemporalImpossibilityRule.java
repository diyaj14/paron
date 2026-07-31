package org.paron.fraudservice.rules;

import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@Slf4j
public class TemporalImpossibilityRule implements FraudRule {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public double evaluate(TransactionEvent event) {
        if (event.getTransactedAt() == null || event.getTransactedAt().isBlank()) {
            return 0.0;
        }
        if (event.getTokenExpiryTime() == null || event.getTokenExpiryTime().isBlank()) {
            return 0.0;
        }

        try {
            LocalDateTime transactedAt = LocalDateTime.parse(event.getTransactedAt(), FORMATTER);
            LocalDateTime tokenExpiry = LocalDateTime.parse(event.getTokenExpiryTime(), FORMATTER);

            if (transactedAt.isAfter(tokenExpiry)) {
                log.warn("temporal impossibility userId={},transactedAt={},tokenExpiry={}",
                        event.getUserId(), transactedAt, tokenExpiry);
                return 1.0;
            }
        } catch (DateTimeParseException e) {
            log.warn("failed to parse timestamps userId={},transactedAt={},tokenExpiry={}",
                    event.getUserId(), event.getTransactedAt(), event.getTokenExpiryTime());
        }

        return 0.0;
    }

    @Override
    public String name() {
        return "TEMPORAL_IMPOSSIBILITY";
    }
}
