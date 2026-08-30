package com.offlinepay.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterMerchantRequest {

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    private String merchantName;
}