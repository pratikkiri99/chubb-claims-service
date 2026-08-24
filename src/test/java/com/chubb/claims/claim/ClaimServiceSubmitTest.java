package com.chubb.claims.claim;

import com.chubb.claims.claim.dto.SubmitClaimCommand;
import com.chubb.claims.event.ClaimEvent;
import com.chubb.claims.event.ClaimEventPublisher;
import com.chubb.claims.event.ClaimEventType;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.shared.error.PolicyNotActiveException;
import com.chubb.claims.shared.error.PolicyNotFoundException;
import com.chubb.claims.shared.error.ReserveLimitException;
import com.chubb.claims.staff.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceSubmitTest {

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
    void submitsAgainstActivePolicy() {
        when(policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-001", Market.AU))
                .thenReturn(Optional.of(ClaimFixtures.activeAuMotor()));
        when(claimNumberSequence.nextValue()).thenReturn(1L);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Claim claim = claimService.submit(command(new BigDecimal("1000.00"), LocalDate.of(2024, 6, 1)));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.OPEN);
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("1000.00");
        assertThat(claim.getClaimNumber()).isEqualTo("CLM-AU-00000001");
        assertThat(claim.getAssignedStaff()).isNull();
        ArgumentCaptor<ClaimEvent> event = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(ClaimEventType.CLAIM_SUBMITTED);
    }

    @Test
    void missingPolicy() {
        when(policyRepository.findByPolicyNumberAndMarket("MISSING", Market.AU)).thenReturn(Optional.empty());
        SubmitClaimCommand cmd = new SubmitClaimCommand(
                "MISSING", Market.AU, "Pat", "pat@example.com", "+61400000000",
                LocalDate.of(2024, 6, 1), "Sydney", "Crash", new BigDecimal("1000.00"));
        assertThatThrownBy(() -> claimService.submit(cmd)).isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void lapsedPolicy() {
        when(policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-LAPSED", Market.AU))
                .thenReturn(Optional.of(ClaimFixtures.lapsedAuMotor()));
        SubmitClaimCommand cmd = command(new BigDecimal("1000.00"), LocalDate.of(2024, 6, 1));
        cmd = new SubmitClaimCommand(
                "POL-AU-MOTOR-LAPSED", cmd.market(), cmd.claimantName(), cmd.claimantEmail(),
                cmd.claimantPhone(), cmd.incidentDate(), cmd.incidentLocation(),
                cmd.incidentDescription(), cmd.claimedAmount());
        SubmitClaimCommand lapsed = cmd;
        assertThatThrownBy(() -> claimService.submit(lapsed)).isInstanceOf(PolicyNotActiveException.class);
    }

    @Test
    void claimedExceedsSumInsured() {
        when(policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-001", Market.AU))
                .thenReturn(Optional.of(ClaimFixtures.activeAuMotor()));
        assertThatThrownBy(() -> claimService.submit(command(new BigDecimal("50000.01"), LocalDate.of(2024, 6, 1))))
                .isInstanceOf(ReserveLimitException.class);
    }

    @Test
    void futureIncidentDate() {
        when(policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-001", Market.AU))
                .thenReturn(Optional.of(ClaimFixtures.activeAuMotor()));
        assertThatThrownBy(() -> claimService.submit(command(new BigDecimal("1000.00"), LocalDate.of(2024, 6, 16))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SubmitClaimCommand command(BigDecimal amount, LocalDate incidentDate) {
        return new SubmitClaimCommand(
                "POL-AU-MOTOR-001", Market.AU, "Pat Claimant", "pat.claimant@example.com",
                "+61400000000", incidentDate, "Sydney", "Rear-end collision", amount);
    }
}
