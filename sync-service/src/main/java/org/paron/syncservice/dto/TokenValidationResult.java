package org.paron.syncservice.dto;

import java.math.BigDecimal;
/*when sync calls /api/v1/tokens/validate , (its same as
* validatetokenResponse of tokenresponse)
 */

public class TokenValidationResult {
    private boolean valid;
    private String userId;
    private String reservartionId;
    private BigDecimal maxAmount;
    private BigDecimal spentAmount;
    private String reason;
}
