package com.chubb.claims.claim;

import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;

import java.math.BigDecimal;

public interface ExposureProjection {

    Market getMarket();

    CoverageType getCoverageType();

    BigDecimal getTotalReserve();

    long getClaimCount();
}
