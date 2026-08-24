package com.chubb.claims.claim.api;

import com.chubb.claims.claim.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record StaffClaimSummaryResponse(
        String claimNumber,
        ClaimStatus status,
        BigDecimal claimedAmount,
        Instant createdAt
) {
}
