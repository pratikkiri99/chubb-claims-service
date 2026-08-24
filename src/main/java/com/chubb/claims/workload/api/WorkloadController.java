package com.chubb.claims.workload.api;

import com.chubb.claims.workload.WorkloadService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/workload")
public class WorkloadController {

    private final WorkloadService workloadService;

    public WorkloadController(WorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    @Operation(summary = "Team workload and performance")
    @GetMapping
    public WorkloadResponse get(@RequestHeader("X-Staff-Id") UUID staffId) {
        return WorkloadResponse.from(workloadService.get(staffId));
    }
}
