package org.paron.syncservice.dto.adjudicate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * One piece of evidence the judge gathered, e.g. "SIGNATURE_TXN_A passed".
 * Kept in the verdict so the ruling is auditable — the panel can see exactly
 * why the judge decided the way it did.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceCheck {
    private String check;
    private boolean passed;
    private String detail;
}