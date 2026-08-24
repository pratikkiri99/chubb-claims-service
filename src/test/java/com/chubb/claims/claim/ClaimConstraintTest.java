package com.chubb.claims.claim;

import com.chubb.claims.AbstractJpaTest;
import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimConstraintTest extends AbstractJpaTest {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ClaimNumberSequence claimNumberSequence;

    @Test
    void rejectsOpenClaimWithAssignee() {
        Claim claim = openClaim();
        Staff staff = staffRepository.findById(
                UUID.fromString("11111111-1111-1111-1111-111111111111")).orElseThrow();
        claim.setAssignedStaff(staff);
        claim.setAssignedAt(Instant.now());

        assertThatThrownBy(() -> claimRepository.saveAndFlush(claim))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSettledClaimWithoutSettlementAmount() {
        Claim claim = openClaim();
        Staff staff = staffRepository.findById(
                UUID.fromString("11111111-1111-1111-1111-111111111111")).orElseThrow();
        claim.setStatus(ClaimStatus.SETTLED);
        claim.setAssignedStaff(staff);
        claim.setAssignedAt(Instant.now());
        claim.setDecidedAt(Instant.now());
        claim.setSettlementAmount(null);
        claim.setRejectionReason(null);

        assertThatThrownBy(() -> claimRepository.saveAndFlush(claim))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsZeroClaimedAmount() {
        Claim claim = openClaim();
        claim.setClaimedAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> claimRepository.saveAndFlush(claim))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Claim openClaim() {
        Policy policy = policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-001", Market.AU)
                .orElseThrow();
        return Claim.open(
                ClaimNumbers.format(Market.AU, claimNumberSequence.nextValue()),
                policy,
                "Pat Claimant",
                "pat.claimant@example.com",
                "+61400000000",
                LocalDate.of(2024, 6, 1),
                "Sydney",
                "Rear-end collision",
                new BigDecimal("1000.00"));
    }
}
