package com.chubb.claims.policy;

import com.chubb.claims.AbstractJpaTest;
import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyRepositoryTest extends AbstractJpaTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    void savesAndLoadsActivePolicy() {
        Policy policy = newPolicy("POL-AU-MOTOR-NEW", Market.AU);

        Policy saved = policyRepository.saveAndFlush(policy);

        Policy loaded = policyRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getPolicyNumber()).isEqualTo("POL-AU-MOTOR-NEW");
        assertThat(loaded.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(loaded.getMarket()).isEqualTo(Market.AU);
        assertThat(policyRepository.findByPolicyNumberAndMarket("POL-AU-MOTOR-NEW", Market.AU)).isPresent();
    }

    @Test
    void rejectsDuplicatePolicyNumberAndMarket() {
        policyRepository.saveAndFlush(newPolicy("POL-AU-DUP-001", Market.AU));

        assertThatThrownBy(() -> policyRepository.saveAndFlush(newPolicy("POL-AU-DUP-001", Market.AU)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Policy newPolicy(String number, Market market) {
        Policy policy = new Policy();
        policy.setPolicyNumber(number);
        policy.setMarket(market);
        policy.setCoverageType(CoverageType.MOTOR);
        policy.setHolderName("Test Holder");
        policy.setSumInsured(new BigDecimal("10000.00"));
        policy.setStatus(PolicyStatus.ACTIVE);
        return policy;
    }
}
