package com.chubb.claims.claim;

import com.chubb.claims.policy.Policy;
import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.Staff;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claim")
@Getter
@Setter
@NoArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoverageType coverageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(nullable = false)
    private String claimantName;

    @Column(nullable = false)
    private String claimantEmail;

    @Column(nullable = false)
    private String claimantPhone;

    @Column(nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false)
    private String incidentLocation;

    @Column(nullable = false)
    private String incidentDescription;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal claimedAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reserveAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal settlementAmount;

    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private Staff assignedStaff;

    private Instant assignedAt;

    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "claim", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<ClaimCommunication> communications = new ArrayList<>();

    public static Claim open(
            String claimNumber,
            Policy policy,
            String claimantName,
            String claimantEmail,
            String claimantPhone,
            LocalDate incidentDate,
            String incidentLocation,
            String incidentDescription,
            BigDecimal claimedAmount) {
        Claim claim = new Claim();
        claim.claimNumber = claimNumber;
        claim.policy = policy;
        claim.market = policy.getMarket();
        claim.coverageType = policy.getCoverageType();
        claim.status = ClaimStatus.OPEN;
        claim.claimantName = claimantName;
        claim.claimantEmail = claimantEmail;
        claim.claimantPhone = claimantPhone;
        claim.incidentDate = incidentDate;
        claim.incidentLocation = incidentLocation;
        claim.incidentDescription = incidentDescription;
        claim.claimedAmount = claimedAmount;
        claim.reserveAmount = claimedAmount;
        return claim;
    }

    public void addCommunication(ClaimCommunication communication) {
        communication.setClaim(this);
        communications.add(communication);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Claim other = (Claim) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
