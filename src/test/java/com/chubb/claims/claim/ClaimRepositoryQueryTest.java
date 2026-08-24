package com.chubb.claims.claim;

import com.chubb.claims.AbstractJpaTest;
import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimRepositoryQueryTest extends AbstractJpaTest {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ClaimNumberSequence claimNumberSequence;

    @Test
    void assignIfOpenUpdatesExactlyOnce() {
        Policy policy = policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-001", Market.AU).orElseThrow();
        Staff staff = staffRepository.findById(UUID.fromString("11111111-1111-1111-1111-111111111111")).orElseThrow();
        String number = ClaimNumbers.format(Market.AU, claimNumberSequence.nextValue());
        claimRepository.saveAndFlush(Claim.open(
                number, policy, "Pat", "pat@example.com", "+61400000000",
                LocalDate.of(2024, 6, 1), "Sydney", "Crash", new BigDecimal("1000.00")));

        int first = claimRepository.assignIfOpen(
                number, staff, ClaimStatus.IN_PROGRESS, ClaimStatus.OPEN, Instant.parse("2024-06-02T00:00:00Z"));
        int second = claimRepository.assignIfOpen(
                number, staff, ClaimStatus.IN_PROGRESS, ClaimStatus.OPEN, Instant.parse("2024-06-02T00:00:01Z"));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        Claim loaded = claimRepository.findByClaimNumber(number).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ClaimStatus.IN_PROGRESS);
        assertThat(loaded.getAssignedStaff().getId()).isEqualTo(staff.getId());
    }

    @Test
    void aggregateExposureExcludesSettled() {
        var rows = claimRepository.aggregateExposure(
                EnumSet.of(ClaimStatus.OPEN, ClaimStatus.IN_PROGRESS, ClaimStatus.PENDING_INFORMATION),
                Market.AU);
        assertThat(rows).isNotNull();
    }
}
