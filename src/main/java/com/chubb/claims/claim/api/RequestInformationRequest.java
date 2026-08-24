package com.chubb.claims.claim.api;

import jakarta.validation.constraints.NotBlank;

public record RequestInformationRequest(@NotBlank String body) {
}
