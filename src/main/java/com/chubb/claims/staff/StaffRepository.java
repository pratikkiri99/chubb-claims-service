package com.chubb.claims.staff;

import com.chubb.claims.shared.domain.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Optional<Staff> findByIdAndActiveTrue(UUID id);

    List<Staff> findByMarketAndTeam(Market market, String team);

    List<Staff> findByMarket(Market market);
}
