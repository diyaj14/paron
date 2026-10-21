package com.offlinepay.ledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class FundingCreditRequest {
    @NotNull private UUID fundingId;
    @NotBlank private String userId;
    @NotNull @DecimalMin(value = "0.01") private BigDecimal amount;
}
