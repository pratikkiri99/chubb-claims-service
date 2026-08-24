package com.chubb.claims.claim;

import com.chubb.claims.shared.domain.Market;

public final class ClaimNumbers {

    private ClaimNumbers() {
    }

    public static String format(Market market, long sequence) {
        return "CLM-%s-%08d".formatted(market.name(), sequence);
    }
}
