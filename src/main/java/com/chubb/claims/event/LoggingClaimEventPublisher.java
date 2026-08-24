package com.chubb.claims.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingClaimEventPublisher implements ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingClaimEventPublisher.class);

    @Override
    public void publish(ClaimEvent event) {
        log.info("claim event type={} claimNumber={} payload={}",
                event.type(), event.claimNumber(), event.payload());
    }
}
