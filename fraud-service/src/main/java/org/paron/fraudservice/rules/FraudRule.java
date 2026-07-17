package org.paron.fraudservice.rules;

import org.paron.fraudservice.dto.TransactionEvent;

public interface FraudRule {
    double evaluate(TransactionEvent event);
    String name();
}
