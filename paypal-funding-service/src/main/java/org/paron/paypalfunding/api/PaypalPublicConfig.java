package org.paron.paypalfunding.api;

import java.math.BigDecimal;

/** Safe to return to a browser: PayPal client IDs are publishable identifiers. */
public record PaypalPublicConfig(String clientId, String currency, BigDecimal walletCreditRate,
                                 BigDecimal maximumAmount) { }
