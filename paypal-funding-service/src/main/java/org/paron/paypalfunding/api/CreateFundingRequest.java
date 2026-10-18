package org.paron.paypalfunding.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CreateFundingRequest(@NotBlank String userId, @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}
