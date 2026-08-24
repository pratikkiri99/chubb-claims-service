package com.chubb.claims.claim;

import com.chubb.claims.AbstractJpaTest;
import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.shared.domain.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimRepositoryTest extends AbstractJpaTest {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private ClaimNumberSequence claimNumberSequence;

    @Test
    void persistsOpenClaimAndFindsByClaimNumber() {
        Policy policy = auMotorPolicy();
        String claimNumber = ClaimNumbers.format(Market.AU, claimNumberSequence.nextValue());

        Claim saved = claimRepository.saveAndFlush(openClaim(policy, claimNumber, new BigDecimal("1000.00")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getClaimNumber()).matches("CLM-AU-\\d{8}");
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.OPEN);
        assertThat(saved.getAssignedStaff()).isNull();
        assertThat(saved.getReserveAmount()).isEqualByComparingTo("1000.00");

        Claim loaded = claimRepository.findByClaimNumber(claimNumber).orElseThrow();
        assertThat(loaded.getClaimantName()).isEqualTo("Pat Claimant");
    }

    @Test
    void cascadesCommunicationPersist() {
        Policy policy = auMotorPolicy();
        String claimNumber = ClaimNumbers.format(Market.AU, claimNumberSequence.nextValue());
        Claim claim = openClaim(policy, claimNumber, new BigDecimal("500.00"));
        claim.addCommunication(ClaimCommunication.claimantResponse("Photos attached"));

        claimRepository.saveAndFlush(claim);

        Claim loaded = claimRepository.findByClaimNumber(claimNumber).orElseThrow();
        assertThat(loaded.getCommunications()).hasSize(1);
        assertThat(loaded.getCommunications().getFirst().getKind()).isEqualTo(CommunicationKind.INFORMATION_RESPONSE);
        assertThat(loaded.getCommunications().getFirst().getStaff()).isNull();
    }

    @Test
    void aggregatesExposureForOpenClaims() {
        Policy policy = auMotorPolicy();
        String claimNumber = ClaimNumbers.format(Market.AU, claimNumberSequence.nextValue());
        claimRepository.saveAndFlush(openClaim(policy, claimNumber, new BigDecimal("2500.00")));

        List<ExposureProjection> rows = claimRepository.aggregateExposure(
                Set.of(ClaimStatus.OPEN, ClaimStatus.IN_PROGRESS, ClaimStatus.PENDING_INFORMATION),
                Market.AU);

        assertThat(rows).isNotEmpty();
        assertThat(rows.stream().mapToLong(ExposureProjection::getClaimCount).sum()).isGreaterThanOrEqualTo(1);
        assertThat(rows.stream()
                .map(ExposureProjection::getTotalReserve)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isGreaterThanOrEqualTo(new BigDecimal("2500.00"));
    }

    private Policy auMotorPolicy() {
        return policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-001", Market.AU).orElseThrow();
    }

    private static Claim openClaim(Policy policy, String claimNumber, BigDecimal amount) {
        return Claim.open(
                claimNumber,
                policy,
                "Pat Claimant",
                "pat.claimant@example.com",
                "+61400000000",
                LocalDate.of(2024, 6, 1),
                "Sydney",
                "Rear-end collision",
                amount);
    }
}
