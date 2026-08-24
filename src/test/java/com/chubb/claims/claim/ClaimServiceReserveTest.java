package com.chubb.claims.claim;

import com.chubb.claims.event.ClaimEventPublisher;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.error.IllegalClaimStateException;
import com.chubb.claims.shared.error.ReserveLimitException;
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
class ClaimServiceReserveTest {

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
    void updateReserveWhileInProgress() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        Claim updated = claimService.updateReserve(
                "CLM-AU-00000001", ClaimFixtures.OFFICER_ID, new BigDecimal("750.00"));

        assertThat(updated.getReserveAmount()).isEqualByComparingTo("750.00");
    }

    @Test
    void updateReserveOnOpenIsIllegal() {
        Staff officer = ClaimFixtures.officer();
        Claim open = ClaimFixtures.openClaim();
        open.setAssignedStaff(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(open));

        assertThatThrownBy(() ->
                claimService.updateReserve("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, new BigDecimal("750.00")))
                .isInstanceOf(IllegalClaimStateException.class);
    }

    @Test
    void negativeReserveRejected() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() ->
                claimService.updateReserve("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, new BigDecimal("-1.00")))
                .isInstanceOf(ReserveLimitException.class);
    }

    @Test
    void reserveAboveSumInsured() {
        Staff officer = ClaimFixtures.officer();
        Claim claim = ClaimFixtures.inProgressClaim(officer);
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID)).thenReturn(Optional.of(officer));
        when(claimRepository.findByClaimNumber("CLM-AU-00000001")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() ->
                claimService.updateReserve("CLM-AU-00000001", ClaimFixtures.OFFICER_ID, new BigDecimal("50000.01")))
                .isInstanceOf(ReserveLimitException.class);
    }
}
