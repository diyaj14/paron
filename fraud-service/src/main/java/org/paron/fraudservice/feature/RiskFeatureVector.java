package org.paron.fraudservice.feature;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RiskFeatureVector(
        @JsonProperty("featureSchemaVersion") String featureSchemaVersion,
        @JsonProperty("amount") Double amount,
        @JsonProperty("amount_to_token_limit_ratio") Double amountToTokenLimitRatio,
        @JsonProperty("merchant_amount_deviation") Double merchantAmountDeviation,
        @JsonProperty("user_tx_count_5m") Integer userTxCount5m,
        @JsonProperty("user_tx_count_1h") Integer userTxCount1h,
        @JsonProperty("user_tx_count_24h") Integer userTxCount24h,
        @JsonProperty("device_tx_count_5m") Integer deviceTxCount5m,
        @JsonProperty("device_tx_count_1h") Integer deviceTxCount1h,
        @JsonProperty("device_tx_count_24h") Integer deviceTxCount24h,
        @JsonProperty("token_tx_count_24h") Integer tokenTxCount24h,
        @JsonProperty("user_tx_value_1h") Double userTxValue1h,
        @JsonProperty("device_tx_value_24h") Double deviceTxValue24h,
        @JsonProperty("token_age_seconds") Double tokenAgeSeconds,
        @JsonProperty("time_to_expiry_seconds") Double timeToExpirySeconds,
        @JsonProperty("offline_duration_seconds") Double offlineDurationSeconds,
        @JsonProperty("token_reuse_count") Integer tokenReuseCount,
        @JsonProperty("duplicate_payload_hash_count") Integer duplicatePayloadHashCount,
        @JsonProperty("previous_settlement_failed") Integer previousSettlementFailed,
        @JsonProperty("merchant_risk_aggregate") Double merchantRiskAggregate,
        @JsonProperty("hour_of_day") Integer hourOfDay,
        @JsonProperty("day_of_week") Integer dayOfWeek,
        @JsonProperty("history_available") Integer historyAvailable,
        @JsonProperty("token_age_known") Integer tokenAgeKnown,
        @JsonProperty("expiry_known") Integer expiryKnown
) {
    public static final String SCHEMA_VERSION = "v1";

    public java.util.Map<String, Double> toMap() {
        java.util.Map<String, Double> map = new java.util.LinkedHashMap<>();
        map.put("amount", amount);
        map.put("amount_to_token_limit_ratio", amountToTokenLimitRatio);
        map.put("merchant_amount_deviation", merchantAmountDeviation);
        map.put("user_tx_count_5m", userTxCount5m != null ? userTxCount5m.doubleValue() : 0.0);
        map.put("user_tx_count_1h", userTxCount1h != null ? userTxCount1h.doubleValue() : 0.0);
        map.put("user_tx_count_24h", userTxCount24h != null ? userTxCount24h.doubleValue() : 0.0);
        map.put("device_tx_count_5m", deviceTxCount5m != null ? deviceTxCount5m.doubleValue() : 0.0);
        map.put("device_tx_count_1h", deviceTxCount1h != null ? deviceTxCount1h.doubleValue() : 0.0);
        map.put("device_tx_count_24h", deviceTxCount24h != null ? deviceTxCount24h.doubleValue() : 0.0);
        map.put("token_tx_count_24h", tokenTxCount24h != null ? tokenTxCount24h.doubleValue() : 0.0);
        map.put("user_tx_value_1h", userTxValue1h);
        map.put("device_tx_value_24h", deviceTxValue24h);
        map.put("token_age_seconds", tokenAgeSeconds);
        map.put("time_to_expiry_seconds", timeToExpirySeconds);
        map.put("offline_duration_seconds", offlineDurationSeconds);
        map.put("token_reuse_count", tokenReuseCount != null ? tokenReuseCount.doubleValue() : 0.0);
        map.put("duplicate_payload_hash_count", duplicatePayloadHashCount != null ? duplicatePayloadHashCount.doubleValue() : 0.0);
        map.put("previous_settlement_failed", previousSettlementFailed != null ? previousSettlementFailed.doubleValue() : 0.0);
        map.put("merchant_risk_aggregate", merchantRiskAggregate);
        map.put("hour_of_day", hourOfDay != null ? hourOfDay.doubleValue() : 0.0);
        map.put("day_of_week", dayOfWeek != null ? dayOfWeek.doubleValue() : 0.0);
        map.put("history_available", historyAvailable != null ? historyAvailable.doubleValue() : 0.0);
        map.put("token_age_known", tokenAgeKnown != null ? tokenAgeKnown.doubleValue() : 0.0);
        map.put("expiry_known", expiryKnown != null ? expiryKnown.doubleValue() : 0.0);
        return map;
    }
}
