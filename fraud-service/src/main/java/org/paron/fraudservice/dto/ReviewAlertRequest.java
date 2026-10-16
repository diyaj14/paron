package org.paron.fraudservice.dto;

import lombok.Data;

@Data
public class ReviewAlertRequest {
    private String status;
    private String reviewerNotes;
}
