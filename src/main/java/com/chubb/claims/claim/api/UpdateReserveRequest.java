package com.chubb.claims.claim.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateReserveRequest(
        @NotNull @DecimalMin("0.00") BigDecimal reserveAmount
) {
}
