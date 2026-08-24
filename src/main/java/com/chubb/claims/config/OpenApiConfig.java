package com.chubb.claims.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI claimsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Chubb Claims Service")
                .version("0.1.0")
                .description("Staff endpoints require header X-Staff-Id (seeded staff UUID). "
                        + "Claimant endpoints are unauthenticated and keyed by claimNumber."));
    }
}
