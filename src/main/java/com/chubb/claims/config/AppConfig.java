package com.chubb.claims.config;

import com.chubb.claims.event.ClaimEventPublisher;
import com.chubb.claims.event.LoggingClaimEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ClaimEventPublisher claimEventPublisher() {
        return new LoggingClaimEventPublisher();
    }
}
