package com.chubb.claims.claim.api;

import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ClaimantClaimResponse(
        String claimNumber,
        ClaimStatus status,
        Market market,
        CoverageType coverageType,
        String claimantName,
        LocalDate incidentDate,
        String incidentLocation,
        String incidentDescription,
        BigDecimal claimedAmount,
        BigDecimal settlementAmount,
        String rejectionReason,
        String latestInformationRequest,
        Instant createdAt,
        Instant updatedAt
) {
}
