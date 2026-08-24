package com.chubb.claims.claim.api;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimService;
import com.chubb.claims.claim.dto.SubmitClaimCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimantClaimController {

    private final ClaimService claimService;
    private final ClaimMapper claimMapper;

    public ClaimantClaimController(ClaimService claimService, ClaimMapper claimMapper) {
        this.claimService = claimService;
        this.claimMapper = claimMapper;
    }

    @Operation(summary = "Report an incident")
    @ApiResponse(responseCode = "201", description = "Claim created")
    @PostMapping
    public ResponseEntity<ClaimantClaimResponse> submit(@Valid @RequestBody SubmitClaimRequest request) {
        Claim claim = claimService.submit(new SubmitClaimCommand(
                request.policyNumber(),
                request.market(),
                request.claimantName(),
                request.claimantEmail(),
                request.claimantPhone(),
                request.incidentDate(),
                request.incidentLocation(),
                request.incidentDescription(),
                request.claimedAmount()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/claims/" + claim.getClaimNumber()))
                .body(claimMapper.toClaimant(claim));
    }

    @Operation(summary = "Track a claim")
    @GetMapping("/{claimNumber}")
    public ClaimantClaimResponse track(@PathVariable String claimNumber) {
        return claimMapper.toClaimant(claimService.getByClaimNumber(claimNumber));
    }

    @Operation(summary = "Provide additional information")
    @PostMapping("/{claimNumber}/information")
    public ClaimantClaimResponse provideInformation(
            @PathVariable String claimNumber,
            @Valid @RequestBody ProvideInformationRequest request) {
        return claimMapper.toClaimant(claimService.provideInformation(claimNumber, request.body()));
    }
}
