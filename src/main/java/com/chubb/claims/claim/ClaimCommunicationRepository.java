package com.chubb.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimCommunicationRepository extends JpaRepository<ClaimCommunication, UUID> {
}
