package com.skyward.accrual;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accrual ingest endpoint. For Day 2 this lives on the core service; on Day 5 the partner-specific
 * adapter (its own deployable) translates partner formats/auth and forwards to here.
 */
@RestController
@RequestMapping("/accruals")
public class AccrualController {

    private final AccrualService accrualService;

    public AccrualController(AccrualService accrualService) {
        this.accrualService = accrualService;
    }

    @PostMapping
    public AccrualResponse accrue(@Valid @RequestBody AccrualRequest request) {
        return accrualService.accrue(request);
    }
}
