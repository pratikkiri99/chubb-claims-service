package com.chubb.claims.claim;

import com.chubb.claims.AbstractJpaTest;
import com.chubb.claims.shared.domain.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimNumberSequenceTest extends AbstractJpaTest {

    @Autowired
    private ClaimNumberSequence claimNumberSequence;

    @Test
    void nextValuesAreStrictlyIncreasing() {
        long first = claimNumberSequence.nextValue();
        long second = claimNumberSequence.nextValue();

        assertThat(second).isGreaterThan(first);
        assertThat(ClaimNumbers.format(Market.AU, first)).matches("CLM-AU-\\d{8}");
    }
}
