package org.paron.syncservice.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
/*when sync calls /api/v1/tokens/validate , (its same as
* validatetokenResponse of tokenresponse)
 */
@Data
@Builder
public class TokenValidationResult {
    private boolean valid;
    private String userId;
    private String reservationId;
    private BigDecimal maxAmount;
    private BigDecimal spentAmount;
    private String reason;
}
