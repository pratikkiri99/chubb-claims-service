package com.chubb.claims.claim;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresClaimNumberSequence implements ClaimNumberSequence {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long nextValue() {
        Number value = (Number) entityManager
                .createNativeQuery("SELECT nextval('claim_number_seq')")
                .getSingleResult();
        return value.longValue();
    }
}
