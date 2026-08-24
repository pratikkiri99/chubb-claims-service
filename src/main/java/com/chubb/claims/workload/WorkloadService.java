package com.chubb.claims.workload;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimRepository;
import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.shared.error.StaffUnauthorizedException;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import com.chubb.claims.staff.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkloadService {

    private static final Collection<ClaimStatus> TERMINAL =
            EnumSet.of(ClaimStatus.SETTLED, ClaimStatus.REJECTED);

    private final StaffRepository staffRepository;
    private final ClaimRepository claimRepository;
    private final Clock clock;

    public WorkloadService(StaffRepository staffRepository, ClaimRepository claimRepository, Clock clock) {
        this.staffRepository = staffRepository;
        this.claimRepository = claimRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkloadSnapshot get(UUID staffId) {
        Staff staff = staffRepository.findByIdAndActiveTrue(staffId)
                .orElseThrow(StaffUnauthorizedException::new);
        Market market = staff.getMarket();
        long queueCount = claimRepository.countByMarketAndStatusAndAssignedStaffIsNull(market, ClaimStatus.OPEN);
        List<Claim> myClaims = claimRepository.findByAssignedStaffAndStatusNotIn(staff, TERMINAL);

        List<Staff> teamStaff = staff.getRole() == StaffRole.MANAGER
                ? staffRepository.findByMarket(market)
                : staffRepository.findByMarketAndTeam(market, staff.getTeam());

        List<Claim> scopedNonTerminal = staff.getRole() == StaffRole.MANAGER
                ? claimRepository.findByMarketAndStatusNotIn(market, TERMINAL)
                : claimRepository.findByAssignedStaffTeamAndMarketAndStatusNotIn(staff.getTeam(), market, TERMINAL);

        Instant cutoff = clock.instant().minus(30, ChronoUnit.DAYS);
        List<Claim> decided = staff.getRole() == StaffRole.MANAGER
                ? claimRepository.findByMarketAndDecidedAtGreaterThanEqual(market, cutoff)
                : claimRepository.findByAssignedStaffTeamAndMarketAndDecidedAtGreaterThanEqual(
                        staff.getTeam(), market, cutoff);

        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            counts.put(status, 0L);
        }
        counts.put(ClaimStatus.OPEN, queueCount);
        scopedNonTerminal.stream()
                .filter(claim -> claim.getStatus() != ClaimStatus.OPEN)
                .forEach(claim -> counts.merge(claim.getStatus(), 1L, Long::sum));

        Map<UUID, List<Claim>> byAssignee = scopedNonTerminal.stream()
                .filter(claim -> claim.getAssignedStaff() != null)
                .collect(Collectors.groupingBy(claim -> claim.getAssignedStaff().getId()));

        List<WorkloadSnapshot.OfficerLoad> teamByOfficer = teamStaff.stream()
                .filter(member -> member.getRole() == StaffRole.OFFICER)
                .map(member -> toLoad(member, byAssignee.getOrDefault(member.getId(), List.of())))
                .toList();

        long settled = decided.stream().filter(c -> c.getStatus() == ClaimStatus.SETTLED).count();
        long rejected = decided.stream().filter(c -> c.getStatus() == ClaimStatus.REJECTED).count();
        List<Claim> withDuration = decided.stream()
                .filter(c -> c.getAssignedAt() != null && c.getDecidedAt() != null)
                .toList();
        Double averageHours = withDuration.isEmpty()
                ? null
                : withDuration.stream()
                .mapToDouble(c -> Duration.between(c.getAssignedAt(), c.getDecidedAt()).toMinutes() / 60.0)
                .average()
                .orElseThrow();

        return new WorkloadSnapshot(
                staff.getId(),
                staff.getFullName(),
                staff.getRole(),
                market,
                queueCount,
                myClaims.stream().map(WorkloadSnapshot.ClaimSummary::from).toList(),
                teamByOfficer,
                counts,
                new WorkloadSnapshot.PerformanceSnapshot(settled, rejected, averageHours));
    }

    private static WorkloadSnapshot.OfficerLoad toLoad(Staff member, List<Claim> claims) {
        long inProgress = claims.stream().filter(c -> c.getStatus() == ClaimStatus.IN_PROGRESS).count();
        long pending = claims.stream().filter(c -> c.getStatus() == ClaimStatus.PENDING_INFORMATION).count();
        BigDecimal reserve = claims.stream()
                .filter(c -> !TERMINAL.contains(c.getStatus()))
                .map(Claim::getReserveAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WorkloadSnapshot.OfficerLoad(
                member.getId(), member.getFullName(), inProgress, pending, reserve);
    }
}
