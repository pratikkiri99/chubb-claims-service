package com.chubb.claims.claim;

import com.chubb.claims.staff.Staff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_communication")
@Getter
@Setter
@NoArgsConstructor
public class ClaimCommunication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommunicationKind kind;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthorType authorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(nullable = false)
    private Instant createdAt;

    public static ClaimCommunication staffRequest(Staff staff, String body) {
        ClaimCommunication communication = new ClaimCommunication();
        communication.kind = CommunicationKind.INFORMATION_REQUEST;
        communication.authorType = AuthorType.STAFF;
        communication.staff = staff;
        communication.body = body;
        return communication;
    }

    public static ClaimCommunication claimantResponse(String body) {
        ClaimCommunication communication = new ClaimCommunication();
        communication.kind = CommunicationKind.INFORMATION_RESPONSE;
        communication.authorType = AuthorType.CLAIMANT;
        communication.body = body;
        return communication;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClaimCommunication other = (ClaimCommunication) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
