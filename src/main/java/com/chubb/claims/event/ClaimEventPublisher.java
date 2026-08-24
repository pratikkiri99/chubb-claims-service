package com.chubb.claims.event;

public interface ClaimEventPublisher {

    void publish(ClaimEvent event);
}
