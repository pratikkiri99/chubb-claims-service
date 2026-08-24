package com.chubb.claims.claim;

import com.chubb.claims.event.ClaimEventPublisher;
import com.chubb.claims.event.ClaimEventType;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.error.ClaimAlreadyAssignedException;
import com.chubb.claims.shared.error.StaffForbiddenException;
import com.chubb.claims.shared.error.StaffUnauthorizedException;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceAssignTest {

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
    void assignsOpenClaim() {
        Staff officer = ClaimFixtures.officer();
        Claim open = ClaimFixtures.openClaim();
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(open));
        when(claimRepository.assignIfOpen(eq("CLM-AU-00000001"), eq(officer),
                eq(ClaimStatus.IN_PROGRESS), eq(ClaimStatus.OPEN), eq(NOW)))
                .thenAnswer(invocation -> {
                    open.assign(officer, NOW);
                    return 1;
                });

        Claim assigned = claimService.assignToSelf("CLM-AU-00000001", ClaimFixtures.OFFICER_ID);

        assertThat(assigned.getStatus()).isEqualTo(ClaimStatus.IN_PROGRESS);
        assertThat(assigned.getAssignedStaff()).isEqualTo(officer);
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.argThat(
                event -> event.type() == ClaimEventType.CLAIM_ASSIGNED));
    }

    @Test
    void alreadyInProgressIsAlreadyAssigned() {
        Staff officer = ClaimFixtures.officer();
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001"))
                .thenReturn(Optional.of(ClaimFixtures.inProgressClaim(officer)));

        assertThatThrownBy(() -> claimService.assignToSelf("CLM-AU-00000001", ClaimFixtures.OFFICER_ID))
                .isInstanceOf(ClaimAlreadyAssignedException.class);
    }

    @Test
    void marketMismatchIsForbidden() {
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.HK_OFFICER_ID))
                .thenReturn(Optional.of(ClaimFixtures.hkOfficer()));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001"))
                .thenReturn(Optional.of(ClaimFixtures.openClaim()));

        assertThatThrownBy(() -> claimService.assignToSelf("CLM-AU-00000001", ClaimFixtures.HK_OFFICER_ID))
                .isInstanceOf(StaffForbiddenException.class);
    }

    @Test
    void inactiveStaffIsUnauthorized() {
        when(staffRepository.findByIdAndActiveTrue(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.assignToSelf("CLM-AU-00000001", ClaimFixtures.OFFICER_ID))
                .isInstanceOf(StaffUnauthorizedException.class);
    }
}
