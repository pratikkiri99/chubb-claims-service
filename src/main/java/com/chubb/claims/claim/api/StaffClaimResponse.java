package com.chubb.claims.claim.api;

import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StaffClaimResponse(
        UUID id,
        String claimNumber,
        ClaimStatus status,
        Market market,
        CoverageType coverageType,
        String policyNumber,
        BigDecimal sumInsured,
        String claimantName,
        LocalDate incidentDate,
        String incidentLocation,
        String incidentDescription,
        BigDecimal claimedAmount,
        BigDecimal reserveAmount,
        BigDecimal settlementAmount,
        String rejectionReason,
        UUID assignedStaffId,
        Instant assignedAt,
        Instant decidedAt,
        List<CommunicationResponse> communications,
        Instant createdAt,
        Instant updatedAt
) {
}
