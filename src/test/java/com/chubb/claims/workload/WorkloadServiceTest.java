package com.chubb.claims.workload;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimFixtures;
import com.chubb.claims.claim.ClaimRepository;
import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import com.chubb.claims.staff.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkloadServiceTest {

    private static final Instant NOW = Instant.parse("2024-07-01T00:00:00Z");

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private ClaimRepository claimRepository;

    private WorkloadService workloadService;

    @BeforeEach
    void setUp() {
        workloadService = new WorkloadService(
                staffRepository, claimRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void officerSeesOwnTeamPerformanceWindow() {
        Staff officer = ClaimFixtures.officer();
        Staff other = ClaimFixtures.otherOfficer();
        Claim mine = ClaimFixtures.inProgressClaim(officer);
        Claim decided = ClaimFixtures.inProgressClaim(officer);
        decided.settle(new BigDecimal("100.00"), decided.getPolicy().getSumInsured(),
                Instant.parse("2024-06-20T12:00:00Z"));

        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.countByMarketAndStatusAndAssignedStaffIsNull(Market.AU, ClaimStatus.OPEN))
                .thenReturn(2L);
        when(claimRepository.findByAssignedStaffAndStatusNotIn(eq(officer), any())).thenReturn(List.of(mine));
        when(staffRepository.findByMarketAndTeam(Market.AU, "AU-MOTOR-1")).thenReturn(List.of(officer, other));
        when(claimRepository.findByAssignedStaffTeamAndMarketAndStatusNotIn(eq("AU-MOTOR-1"), eq(Market.AU), any()))
                .thenReturn(List.of(mine));
        when(claimRepository.findByAssignedStaffTeamAndMarketAndDecidedAtGreaterThanEqual(
                eq("AU-MOTOR-1"), eq(Market.AU), any()))
                .thenReturn(List.of(decided));

        WorkloadSnapshot snapshot = workloadService.get(ClaimFixtures.OFFICER_ID);

        assertThat(snapshot.role()).isEqualTo(StaffRole.OFFICER);
        assertThat(snapshot.incomingQueueCount()).isEqualTo(2);
        assertThat(snapshot.myClaims()).hasSize(1);
        assertThat(snapshot.teamByOfficer()).extracting(WorkloadSnapshot.OfficerLoad::staffId)
                .containsExactlyInAnyOrder(ClaimFixtures.OFFICER_ID, ClaimFixtures.OTHER_OFFICER_ID);
        assertThat(snapshot.performance().settledCount()).isEqualTo(1);
        assertThat(snapshot.performance().averageHoursToDecision()).isNotNull();
    }

    @Test
    void managerSeesMarketScope() {
        Staff manager = ClaimFixtures.manager();
        Staff officer = ClaimFixtures.officer();
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(claimRepository.countByMarketAndStatusAndAssignedStaffIsNull(Market.AU, ClaimStatus.OPEN))
                .thenReturn(3L);
        when(claimRepository.findByAssignedStaffAndStatusNotIn(eq(manager), any())).thenReturn(List.of());
        when(staffRepository.findByMarket(Market.AU)).thenReturn(List.of(manager, officer));
        when(claimRepository.findByMarketAndStatusNotIn(eq(Market.AU), any())).thenReturn(List.of());
        when(claimRepository.findByMarketAndDecidedAtGreaterThanEqual(eq(Market.AU), any())).thenReturn(List.of());

        WorkloadSnapshot snapshot = workloadService.get(ClaimFixtures.MANAGER_ID);

        assertThat(snapshot.role()).isEqualTo(StaffRole.MANAGER);
        assertThat(snapshot.incomingQueueCount()).isEqualTo(3);
        assertThat(snapshot.performance().averageHoursToDecision()).isNull();
        assertThat(snapshot.teamByOfficer()).extracting(WorkloadSnapshot.OfficerLoad::staffId)
                .containsExactly(ClaimFixtures.OFFICER_ID);
    }
}
