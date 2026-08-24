package com.chubb.claims.workload.api;

import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.StaffRole;
import com.chubb.claims.workload.WorkloadSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkloadResponse(
        UUID staffId,
        String staffName,
        StaffRole role,
        Market market,
        long incomingQueueCount,
        List<MyClaim> myClaims,
        List<OfficerLoad> teamByOfficer,
        Map<ClaimStatus, Long> countsByStatus,
        Performance performance
) {

    public record MyClaim(String claimNumber, ClaimStatus status, BigDecimal claimedAmount, Instant createdAt) {
    }

    public record OfficerLoad(
            UUID staffId, String name, long inProgress, long pendingInfo, BigDecimal outstandingReserve) {
    }

    public record Performance(long settledCount, long rejectedCount, Double averageHoursToDecision) {
    }

    public static WorkloadResponse from(WorkloadSnapshot snapshot) {
        return new WorkloadResponse(
                snapshot.staffId(),
                snapshot.staffName(),
                snapshot.role(),
                snapshot.market(),
                snapshot.incomingQueueCount(),
                snapshot.myClaims().stream()
                        .map(c -> new MyClaim(c.claimNumber(), c.status(), c.claimedAmount(), c.createdAt()))
                        .toList(),
                snapshot.teamByOfficer().stream()
                        .map(o -> new OfficerLoad(
                                o.staffId(), o.name(), o.inProgress(), o.pendingInfo(), o.outstandingReserve()))
                        .toList(),
                snapshot.countsByStatus(),
                new Performance(
                        snapshot.performance().settledCount(),
                        snapshot.performance().rejectedCount(),
                        snapshot.performance().averageHoursToDecision()));
    }
}
