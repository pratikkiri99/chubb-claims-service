package com.chubb.claims.claim.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DecisionRequest(
        @NotNull DecisionType type,
        BigDecimal settlementAmount,
        String rejectionReason
) {

    @AssertTrue(message = "settlementAmount required when SETTLED")
    public boolean isSettlementValid() {
        return type != DecisionType.SETTLED
                || (settlementAmount != null && settlementAmount.signum() > 0);
    }

    @AssertTrue(message = "rejectionReason required when REJECTED")
    public boolean isRejectionValid() {
        return type != DecisionType.REJECTED
                || (rejectionReason != null && !rejectionReason.isBlank());
    }
}
