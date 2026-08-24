package com.chubb.claims.policy;

import com.chubb.claims.shared.domain.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    Optional<Policy> findByPolicyNumberAndMarket(String policyNumber, Market market);
}
