package com.chubb.claims.claim;

import com.chubb.claims.event.ClaimEventPublisher;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.error.IllegalClaimStateException;
import com.chubb.claims.shared.error.ReserveLimitException;
import com.chubb.claims.shared.error.StaffForbiddenException;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceDecisionTest {

    private static final Instant NOW = Instant.parse("2024-06-15T00:00:00Z");

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private ClaimNumberSequence claimNumberSequence;
    @Mock
    private ClaimEventPublisher eventPublisher;

    private ClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(
                claimRepository, policyRepository, staffRepository,
                claimNumberSequence, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void settleFromInProgress() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        Claim settled = claimService.settle("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, new BigDecimal("900.00"));

        assertThat(settled.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(settled.getSettlementAmount()).isEqualByComparingTo("900.00");
        assertThat(settled.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectFromInProgress() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        Claim rejected = claimService.reject("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, "Not covered");

        assertThat(rejected.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Not covered");
        assertThat(rejected.getSettlementAmount()).isNull();
    }

    @Test
    void settleAboveSumInsured() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() ->
                claimService.settle("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, new BigDecimal("50000.01")))
                .isInstanceOf(ReserveLimitException.class);
    }

    @Test
    void rejectBlankReason() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.reject("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settleTerminalIsIllegal() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        claim.settle(new BigDecimal("100.00"), claim.getPolicy().getSumInsured(), NOW);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() ->
                claimService.settle("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, new BigDecimal("100.00")))
                .isInstanceOf(IllegalClaimStateException.class);
    }

    @Test
    void officerWhoIsNotAssigneeIsForbidden() {
        Staff assignee = ClaimFixtures.officer();
        Staff other = ClaimFixtures.otherOfficer();
        Claim claim = ClaimFixtures.inProgressClaim(assignee);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OTHER_OFFICER_ID)).thenReturn(Optional.of(other));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() ->
                claimService.settle("CLM-AU-00000001", ClaimFixtures.OTHER_OFFICER_ID, new BigDecimal("100.00")))
                .isInstanceOf(StaffForbiddenException.class);
    }

    @Test
    void managerSameMarketMaySettle() {
        Staff officer = ClaimFixtures.officer();
        Staff manager = ClaimFixtures.manager();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        Claim settled = claimService.settle("CLM-AU-00000001", ClaimFixtures.MANAGER_ID, new BigDecimal("100.00"));
        assertThat(settled.getStatus()).isEqualTo(ClaimStatus.SETTLED);
    }
}
