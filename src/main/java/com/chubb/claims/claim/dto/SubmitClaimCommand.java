package com.chubb.claims.claim.dto;

import com.chubb.claims.shared.domain.Market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubmitClaimCommand(
        String policyNumber,
        Market market,
        String claimantName,
        String claimantEmail,
        String claimantPhone,
        LocalDate incidentDate,
        String incidentLocation,
        String incidentDescription,
        BigDecimal claimedAmount
) {
}
