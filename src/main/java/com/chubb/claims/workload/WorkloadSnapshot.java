package com.chubb.claims.workload;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.StaffRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkloadSnapshot(
        UUID staffId,
        String staffName,
        StaffRole role,
        Market market,
        long incomingQueueCount,
        List<ClaimSummary> myClaims,
        List<OfficerLoad> teamByOfficer,
        Map<ClaimStatus, Long> countsByStatus,
        PerformanceSnapshot performance
) {

    public record ClaimSummary(
            String claimNumber,
            ClaimStatus status,
            BigDecimal claimedAmount,
            Instant createdAt
    ) {
        static ClaimSummary from(Claim claim) {
            return new ClaimSummary(
                    claim.getClaimNumber(),
                    claim.getStatus(),
                    claim.getClaimedAmount(),
                    claim.getCreatedAt());
        }
    }

    public record OfficerLoad(
            UUID staffId,
            String name,
            long inProgress,
            long pendingInfo,
            BigDecimal outstandingReserve
    ) {
    }

    public record PerformanceSnapshot(
            long settledCount,
            long rejectedCount,
            Double averageHoursToDecision
    ) {
    }
}
