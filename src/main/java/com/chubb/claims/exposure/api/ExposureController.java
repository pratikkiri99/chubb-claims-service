package com.chubb.claims.exposure.api;

import com.chubb.claims.exposure.ExposureService;
import com.chubb.claims.shared.domain.Market;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/exposure")
public class ExposureController {

    private final ExposureService exposureService;

    public ExposureController(ExposureService exposureService) {
        this.exposureService = exposureService;
    }

    @Operation(summary = "Outstanding liability exposure")
    @GetMapping
    public ExposureResponse get(
            @RequestHeader("X-Staff-Id") UUID staffId,
            @RequestParam(required = false) Market market) {
        return ExposureResponse.from(exposureService.get(staffId, Optional.ofNullable(market)));
    }
}
