package org.paron.paypalfunding.api;

/** Safe to return to a browser: PayPal client IDs are publishable identifiers. */
public record PaypalPublicConfig(String clientId, String currency) { }
