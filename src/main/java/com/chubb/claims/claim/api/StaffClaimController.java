package com.chubb.claims.claim.api;

import com.chubb.claims.claim.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/claims")
public class StaffClaimController {

    private final ClaimService claimService;
    private final ClaimMapper claimMapper;

    public StaffClaimController(ClaimService claimService, ClaimMapper claimMapper) {
        this.claimService = claimService;
        this.claimMapper = claimMapper;
    }

    @Operation(summary = "Incoming unassigned queue")
    @GetMapping("/queue")
    public List<StaffClaimSummaryResponse> queue(@RequestHeader("X-Staff-Id") UUID staffId) {
        return claimService.incomingQueue(staffId).stream().map(claimMapper::toSummary).toList();
    }

    @Operation(summary = "Staff claim detail")
    @GetMapping("/{claimNumber}")
    public StaffClaimResponse get(
            @PathVariable String claimNumber,
            @RequestHeader("X-Staff-Id") UUID staffId) {
        return claimMapper.toStaff(claimService.getForStaff(claimNumber, staffId));
    }

    @Operation(summary = "Pick up an OPEN claim")
    @PostMapping("/{claimNumber}/assignment")
    public StaffClaimResponse assign(
            @PathVariable String claimNumber,
            @RequestHeader("X-Staff-Id") UUID staffId) {
        return claimMapper.toStaff(claimService.assignToSelf(claimNumber, staffId));
    }

    @Operation(summary = "Request information from the claimant")
    @PostMapping("/{claimNumber}/information-requests")
    public StaffClaimResponse requestInformation(
            @PathVariable String claimNumber,
            @RequestHeader("X-Staff-Id") UUID staffId,
            @Valid @RequestBody RequestInformationRequest request) {
        return claimMapper.toStaff(claimService.requestInformation(claimNumber, staffId, request.body()));
    }

    @Operation(summary = "Update reserve")
    @PatchMapping("/{claimNumber}/reserve")
    public StaffClaimResponse updateReserve(
            @PathVariable String claimNumber,
            @RequestHeader("X-Staff-Id") UUID staffId,
            @Valid @RequestBody UpdateReserveRequest request) {
        return claimMapper.toStaff(claimService.updateReserve(claimNumber, staffId, request.reserveAmount()));
    }

    @Operation(summary = "Settle or reject a claim")
    @PostMapping("/{claimNumber}/decision")
    public StaffClaimResponse decide(
            @PathVariable String claimNumber,
            @RequestHeader("X-Staff-Id") UUID staffId,
            @Valid @RequestBody DecisionRequest request) {
        if (request.type() == DecisionType.SETTLED) {
            return claimMapper.toStaff(claimService.settle(claimNumber, staffId, request.settlementAmount()));
        }
        return claimMapper.toStaff(claimService.reject(claimNumber, staffId, request.rejectionReason()));
    }
}
