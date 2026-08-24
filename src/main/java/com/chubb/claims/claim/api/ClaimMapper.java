package com.chubb.claims.claim.api;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimCommunication;
import com.chubb.claims.claim.CommunicationKind;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ClaimMapper {

    public ClaimantClaimResponse toClaimant(Claim claim) {
        return new ClaimantClaimResponse(
                claim.getClaimNumber(),
                claim.getStatus(),
                claim.getMarket(),
                claim.getCoverageType(),
                claim.getClaimantName(),
                claim.getIncidentDate(),
                claim.getIncidentLocation(),
                claim.getIncidentDescription(),
                claim.getClaimedAmount(),
                claim.getSettlementAmount(),
                claim.getRejectionReason(),
                latestInformationRequest(claim),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    public StaffClaimResponse toStaff(Claim claim) {
        return new StaffClaimResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getStatus(),
                claim.getMarket(),
                claim.getCoverageType(),
                claim.getPolicy().getPolicyNumber(),
                claim.getPolicy().getSumInsured(),
                claim.getClaimantName(),
                claim.getIncidentDate(),
                claim.getIncidentLocation(),
                claim.getIncidentDescription(),
                claim.getClaimedAmount(),
                claim.getReserveAmount(),
                claim.getSettlementAmount(),
                claim.getRejectionReason(),
                claim.getAssignedStaff() == null ? null : claim.getAssignedStaff().getId(),
                claim.getAssignedAt(),
                claim.getDecidedAt(),
                claim.getCommunications().stream()
                        .sorted(Comparator.comparing(ClaimCommunication::getCreatedAt))
                        .map(this::toCommunication)
                        .toList(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    public StaffClaimSummaryResponse toSummary(Claim claim) {
        return new StaffClaimSummaryResponse(
                claim.getClaimNumber(),
                claim.getStatus(),
                claim.getClaimedAmount(),
                claim.getCreatedAt());
    }

    public CommunicationResponse toCommunication(ClaimCommunication communication) {
        return new CommunicationResponse(
                communication.getKind(),
                communication.getAuthorType(),
                communication.getBody(),
                communication.getCreatedAt());
    }

    private static String latestInformationRequest(Claim claim) {
        return claim.getCommunications().stream()
                .filter(c -> c.getKind() == CommunicationKind.INFORMATION_REQUEST)
                .max(Comparator.comparing(ClaimCommunication::getCreatedAt))
                .map(ClaimCommunication::getBody)
                .orElse(null);
    }
}
