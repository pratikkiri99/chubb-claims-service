package com.chubb.claims.claim.api;

import com.chubb.claims.shared.domain.Market;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubmitClaimRequest(
        @NotBlank String policyNumber,
        @NotNull Market market,
        @NotBlank String claimantName,
        @Email @NotBlank String claimantEmail,
        @NotBlank String claimantPhone,
        @NotNull @PastOrPresent LocalDate incidentDate,
        @NotBlank String incidentLocation,
        @NotBlank String incidentDescription,
        @NotNull @Positive BigDecimal claimedAmount
) {
}
