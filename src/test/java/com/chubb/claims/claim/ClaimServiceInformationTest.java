package com.chubb.claims.claim;

import com.chubb.claims.event.ClaimEventPublisher;
import com.chubb.claims.event.ClaimEventType;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.error.IllegalClaimStateException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceInformationTest {

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
    void requestInformationFromInProgress() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        Claim updated = claimService.requestInformation("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, "Need photos");

        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.PENDING_INFORMATION);
        assertThat(updated.getCommunications()).hasSize(1);
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.argThat(
                event -> event.type() == ClaimEventType.INFORMATION_REQUESTED));
    }

    @Test
    void provideInformationFromPending() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        claim.requestInformation(officer, "Need photos");
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        Claim updated = claimService.provideInformation("CLM-AU-00000001", "Photos attached");

        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.IN_PROGRESS);
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.argThat(
                event -> event.type() == ClaimEventType.INFORMATION_PROVIDED));
    }

    @Test
    void requestInformationFromOpenIsIllegal() {
        Staff officer = ClaimFixtures.officer();
        Claim open = ClaimFixtures.openClaim();
        open.setAssignedStaff(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(open));

        assertThatThrownBy(() ->
                claimService.requestInformation("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, "Need photos"))
                .isInstanceOf(IllegalClaimStateException.class);
    }

    @Test
    void provideInformationWhenNotPendingIsIllegal() {
        when(claimRepository.findByClaimNumber("CLM-AU-00000001"))
                .thenReturn(Optional.of(ClaimFixtures.openClaim()));

        assertThatThrownBy(() -> claimService.provideInformation("CLM-AU-00000001", "Photos"))
                .isInstanceOf(IllegalClaimStateException.class);
    }
}
